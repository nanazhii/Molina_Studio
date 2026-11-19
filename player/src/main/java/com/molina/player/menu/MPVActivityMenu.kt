package com.molina.player.menu

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.molina.core.common.diag.Diag
import com.molina.player.DecimalPickerDialog
import com.molina.player.MPVView
import com.molina.player.PickerDialog
import com.molina.player.R
import com.molina.player.SliderPickerDialog
import com.molina.player.StateRestoreCallback
import com.molina.player.SubDelayDialog
import com.molina.player.Utils
import com.molina.player.mpv.MpvGateway
import com.molina.player.mpv.MpvProperties
import kotlin.math.roundToInt

/**
 * Menu layar pemutar (menu atas, menu lanjutan, bab, picker angka, dekoder) yang dulu ada di MPVActivity.
 * Tanggung jawab tunggal: menampilkan dialog dan meneruskan pilihan ke mpv lewat MpvGateway/MpvProperties.
 * Status layar (jeda saat dialog, pemulihan) tetap milik MPVActivity dan diberikan lewat [StateRestoreCallback].
 */
internal class MPVActivityMenu(
    private val activity: AppCompatActivity,
    private val playerProvider: () -> MPVView,
) {
    private val player get() = playerProvider()

    data class MenuItem(@IdRes val idRes: Int, val handler: () -> Boolean)

    fun pickDecoder(restore: StateRestoreCallback) {
        val items = mutableListOf(
            Pair("HW (mediacodec-copy)", "mediacodec-copy"),
            Pair("SW", "no")
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            items.add(0, Pair("HW+ (mediacodec)", "mediacodec"))
        val hwdecActive = player.hwdecActive
        val selectedIndex = items.indexOfFirst { it.second == hwdecActive }
        with (AlertDialog.Builder(activity)) {
            setSingleChoiceItems(items.map { it.first }.toTypedArray(), selectedIndex ) { dialog, idx ->
                MpvProperties.setString("hwdec", items[idx].second)
                dialog.dismiss()
            }
            setOnDismissListener { restore() }
            create().show()
        }
    }

    fun genericMenu(
            @LayoutRes layoutRes: Int, buttons: List<MenuItem>, hiddenButtons: Set<Int>,
            restoreState: StateRestoreCallback) {
        lateinit var dialog: AlertDialog

        val builder = AlertDialog.Builder(activity)
        val dialogView = LayoutInflater.from(builder.context).inflate(layoutRes, null)

        for (button in buttons) {
            val buttonView = dialogView.findViewById<Button>(button.idRes)
            buttonView.setOnClickListener {
                val ret = button.handler()
                if (ret) // restore state immediately
                    restoreState()
                dialog.dismiss()
            }
        }

        hiddenButtons.forEach { dialogView.findViewById<View>(it).isVisible = false }

        if (Utils.visibleChildren(dialogView) == 0) {
            Diag.behavior("player", "menu-empty", "menu kosong, tidak ditampilkan")
            restoreState()
            return
        }

        Utils.handleInsetsAsPadding(dialogView)

        with (builder) {
            setView(dialogView)
            setOnCancelListener { restoreState() }
            dialog = create()
        }
        dialog.show()
    }

    fun genericPickerDialog(
        picker: PickerDialog, @StringRes titleRes: Int, property: String,
        restoreState: StateRestoreCallback
    ) {
        val dialog = with(AlertDialog.Builder(activity)) {
            setTitle(titleRes)
            val inflater = LayoutInflater.from(context)
            setView(picker.buildView(inflater))
            setPositiveButton(R.string.dialog_ok) { _, _ ->
                picker.number?.let {
                    if (picker.isInteger())
                        MpvProperties.setInt(property, it.toInt())
                    else
                        MpvProperties.setDouble(property, it)
                }
            }
            setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
            setOnDismissListener { restoreState() }
            create()
        }

        picker.number = MpvProperties.getDouble(property)
        dialog.show()
    }

    fun openAdvancedMenu(restoreState: StateRestoreCallback) {
        /******/
        val hiddenButtons = mutableSetOf<Int>()
        val buttons: MutableList<MenuItem> = mutableListOf(
                MenuItem(R.id.subSeekPrev) {
                    MpvGateway.command(arrayOf("sub-seek", "-1")); true
                },
                MenuItem(R.id.subSeekNext) {
                    MpvGateway.command(arrayOf("sub-seek", "1")); true
                },
                MenuItem(R.id.statsBtn) {
                    MpvGateway.command(arrayOf("script-binding", "stats/display-stats-toggle")); true
                },
                MenuItem(R.id.aspectBtn) {
                    val ratios = activity.resources.getStringArray(R.array.aspect_ratios)
                    with (AlertDialog.Builder(activity)) {
                        setItems(R.array.aspect_ratio_names) { dialog, item ->
                            if (ratios[item] == "panscan") {
                                MpvProperties.setString("video-aspect-override", "-1")
                                MpvProperties.setDouble("panscan", 1.0)
                            } else {
                                MpvProperties.setString("video-aspect-override", ratios[item])
                                MpvProperties.setDouble("panscan", 0.0)
                            }
                            dialog.dismiss()
                        }
                        setOnDismissListener { restoreState() }
                        create().show()
                    }; false
                },
        )

        val statsButtons = arrayOf(R.id.statsBtn1, R.id.statsBtn2, R.id.statsBtn3)
        for (i in 1..3) {
            buttons.add(MenuItem(statsButtons[i-1]) {
                MpvGateway.command(arrayOf("script-binding", "stats/display-page-$i")); true
            })
        }

        // contrast, brightness and others get a -100 to 100 slider
        val basicIds = arrayOf(R.id.contrastBtn, R.id.brightnessBtn, R.id.gammaBtn, R.id.saturationBtn)
        val basicProps = arrayOf("contrast", "brightness", "gamma", "saturation")
        val basicTitles = arrayOf(R.string.contrast, R.string.video_brightness, R.string.gamma, R.string.saturation)
        basicIds.forEachIndexed { index, id ->
            buttons.add(MenuItem(id) {
                val slider = SliderPickerDialog(-100.0, 100.0, 1, R.string.format_fixed_number)
                genericPickerDialog(slider, basicTitles[index], basicProps[index], restoreState)
                false
            })
        }

        // audio / sub delay get a decimal picker
        buttons.add(MenuItem(R.id.audioDelayBtn) {
            val picker = DecimalPickerDialog(-600.0, 600.0)
            genericPickerDialog(picker, R.string.audio_delay, "audio-delay", restoreState)
            false
        })
        buttons.add(MenuItem(R.id.subDelayBtn) {
            val picker = SubDelayDialog(-600.0, 600.0)
            val dialog = with(AlertDialog.Builder(activity)) {
                setTitle(R.string.sub_delay)
                val inflater = LayoutInflater.from(context)
                setView(picker.buildView(inflater))
                setPositiveButton(R.string.dialog_ok) { _, _ ->
                    picker.delay1?.let { player.subDelay = it }
                    picker.delay2?.let { player.secondarySubDelay = it }
                }
                setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
                setOnDismissListener { restoreState() }
                create()
            }

            picker.delay1 = player.subDelay ?: 0.0
            picker.delay2 = if (player.secondarySid != -1) player.secondarySubDelay else null
            dialog.show()
            false
        })

        if (player.vid == -1)
            hiddenButtons.addAll(arrayOf(R.id.rowVideo1, R.id.rowVideo2, R.id.aspectBtn))
        if (player.aid == -1 || player.vid == -1)
            hiddenButtons.add(R.id.audioDelayBtn)
        if (player.sid == -1 && player.secondarySid == -1)
            hiddenButtons.addAll(arrayOf(R.id.subDelayBtn, R.id.rowSubSeek))
        /******/

        genericMenu(R.layout.dialog_advanced_menu, buttons, hiddenButtons, restoreState)
    }

    // ---- Bab (dipanggil dari menu atas) ----
    fun chapterCount(): Int = MpvProperties.getInt("chapter-list/count") ?: 0

    fun chapterStep(delta: Int) {
        MpvGateway.command(arrayOf("add", "chapter", delta.toString()))
    }

    /** true = tidak ada bab (pemanggil memulihkan status); false = dialog tampil dan memulihkan sendiri. */
    fun showChapterPicker(restoreState: StateRestoreCallback): Boolean {
        val chapters = player.loadChapters()
        if (chapters.isEmpty())
            return true
        val chapterArray = chapters.map {
            val timecode = Utils.prettyTime(it.time.roundToInt())
            if (!it.title.isNullOrEmpty())
                activity.getString(R.string.ui_chapter, it.title, timecode)
            else
                activity.getString(R.string.ui_chapter_fallback, it.index + 1, timecode)
        }.toTypedArray()
        val selectedIndex = MpvProperties.getInt("chapter") ?: 0
        with (AlertDialog.Builder(activity)) {
            setSingleChoiceItems(chapterArray, selectedIndex) { dialog, item ->
                MpvProperties.setInt("chapter", chapters[item].index)
                dialog.dismiss()
            }
            setOnDismissListener { restoreState() }
            create().show()
        }
        return false
    }
}
