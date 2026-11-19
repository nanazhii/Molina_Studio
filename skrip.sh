#!/bin/sh
python3 << 'PYEOF'
import re
import sys
from pathlib import Path

ROOT = Path.cwd()
P = "player/src/main"
if not (ROOT / P).is_dir() or not (ROOT / "settings.gradle.kts").exists():
    sys.exit("GAGAL: jalankan dari root repo Molina_Studio (cd /sdcard/Molina_Studio)")

changed = []


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, content):
    p = ROOT / rel
    if p.exists() and p.read_text(encoding="utf-8") == content:
        return
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8")
    changed.append(rel)


def sub(rel, old, new, marker=None):
    """Ganti satu pola. Aman diulang: lewati bila sudah terpasang."""
    t = read(rel)
    if marker is not None and marker in t:
        return
    if old not in t:
        if new == "" or new in t:
            return
        sys.exit(f"GAGAL: pola tidak ditemukan di {rel}: {old[:70]!r}")
    write(rel, t.replace(old, new, 1))


# ---------------------------------------------------------------- warna
write(f"{P}/res/values/colors_cinema.xml", """<?xml version="1.0" encoding="utf-8"?>
<!-- molina-home-cinema: palet Home Theater (hitam pekat + hijau terang) -->
<resources>
    <color name="molina_cinema_bg">#070908</color>
    <color name="molina_cinema_field">#121512</color>
    <color name="molina_cinema_card">#141714</color>
    <color name="molina_cinema_green">#22C55E</color>
    <color name="molina_cinema_green_dim">#1FA84F</color>
    <color name="molina_cinema_green_fill">#0E2214</color>
    <color name="molina_cinema_line">#363A36</color>
    <color name="molina_cinema_box_line">#262B27</color>
    <color name="molina_cinema_text">#F2F4F2</color>
    <color name="molina_cinema_text2">#B4BAB5</color>
    <color name="molina_cinema_blue">#6FA8DC</color>
    <color name="molina_cinema_purple">#A78BFA</color>
    <color name="molina_cinema_frame_a">#22C55E</color>
    <color name="molina_cinema_frame_b">#3B6FE0</color>
    <color name="molina_cinema_frame_c">#22D3A0</color>
    <color name="molina_cinema_ripple">#3322C55E</color>
</resources>
""")

write(f"{P}/res/color/cinema_btn_text.xml", """<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_enabled="false" android:color="#80FFFFFF" />
    <item android:color="@color/molina_cinema_text" />
</selector>
""")

# ------------------------------------------------------------- drawable
write(f"{P}/res/drawable/bg_cinema_frame.xml", """<?xml version="1.0" encoding="utf-8"?>
<!-- bingkai luar bergradasi: lapis bawah gradasi, lapis atas hitam diinset 2dp -->
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item>
        <shape android:shape="rectangle">
            <gradient
                android:angle="270"
                android:centerColor="@color/molina_cinema_frame_b"
                android:endColor="@color/molina_cinema_frame_c"
                android:startColor="@color/molina_cinema_frame_a" />
            <corners android:radius="16dp" />
        </shape>
    </item>
    <item
        android:bottom="2dp"
        android:left="2dp"
        android:right="2dp"
        android:top="2dp">
        <shape android:shape="rectangle">
            <solid android:color="@color/molina_cinema_bg" />
            <corners android:radius="14dp" />
        </shape>
    </item>
</layer-list>
""")

write(f"{P}/res/drawable/bg_cinema_box.xml", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/molina_cinema_bg" />
    <stroke android:width="1dp" android:color="@color/molina_cinema_box_line" />
    <corners android:radius="6dp" />
</shape>
""")

write(f"{P}/res/drawable/bg_cinema_field.xml", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/molina_cinema_field" />
    <stroke android:width="2dp" android:color="@color/molina_cinema_green" />
    <corners android:radius="4dp" />
</shape>
""")

write(f"{P}/res/drawable/bg_cinema_btn.xml", """<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android" android:color="@color/molina_cinema_ripple">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/molina_cinema_card" />
            <stroke android:width="1.5dp" android:color="@color/molina_cinema_line" />
            <corners android:radius="4dp" />
        </shape>
    </item>
</ripple>
""")

write(f"{P}/res/drawable/bg_cinema_btn_primary.xml", """<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:state_enabled="false">
        <shape android:shape="rectangle">
            <solid android:color="@color/molina_cinema_card" />
            <stroke android:width="1.5dp" android:color="#6622C55E" />
            <corners android:radius="4dp" />
        </shape>
    </item>
    <item>
        <ripple android:color="@color/molina_cinema_ripple">
            <item>
                <shape android:shape="rectangle">
                    <solid android:color="@color/molina_cinema_green_fill" />
                    <stroke android:width="1.5dp" android:color="@color/molina_cinema_green" />
                    <corners android:radius="4dp" />
                </shape>
            </item>
        </ripple>
    </item>
</selector>
""")

write(f"{P}/res/drawable/bg_cinema_card.xml", """<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android" android:color="@color/molina_cinema_ripple">
    <item>
        <shape android:shape="rectangle">
            <solid android:color="@color/molina_cinema_card" />
            <stroke android:width="1.5dp" android:color="@color/molina_cinema_line" />
            <corners android:radius="8dp" />
        </shape>
    </item>
</ripple>
""")

write(f"{P}/res/drawable/bg_cinema_pill.xml", """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="@color/molina_cinema_green_fill" />
    <stroke android:width="1.5dp" android:color="@color/molina_cinema_green" />
    <corners android:radius="16dp" />
</shape>
""")

write(f"{P}/res/drawable/ic_cinema_file.xml", """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path android:fillColor="#E8EAE8" android:pathData="M5,2 L14,2 L19,7 L19,22 L5,22 Z" />
    <path android:fillColor="#B9BEB9" android:pathData="M14,2 L14,7 L19,7 Z" />
    <path
        android:pathData="M8,11 L16,11 M8,14 L16,14 M8,17 L16,17"
        android:strokeColor="#7C827C"
        android:strokeWidth="1.2" />
</vector>
""")

write(f"{P}/res/drawable/ic_cinema_tv.xml", """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M8,3 L12,8 L16,3"
        android:strokeColor="#F97316"
        android:strokeWidth="1.6"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
    <path android:fillColor="#F97316" android:pathData="M4,8 L20,8 Q22,8 22,10 L22,19 Q22,21 20,21 L4,21 Q2,21 2,19 L2,10 Q2,8 4,8 Z" />
    <path android:fillColor="#38BDF8" android:pathData="M5,10.5 L17,10.5 Q18,10.5 18,11.5 L18,17.5 Q18,18.5 17,18.5 L5,18.5 Q4,18.5 4,17.5 L4,11.5 Q4,10.5 5,10.5 Z" />
    <path android:fillColor="#7C2D12" android:pathData="M20,12 m-0.9,0 a0.9,0.9 0 1,0 1.8,0 a0.9,0.9 0 1,0 -1.8,0 M20,16 m-0.9,0 a0.9,0.9 0 1,0 1.8,0 a0.9,0.9 0 1,0 -1.8,0" />
</vector>
""")

write(f"{P}/res/drawable/ic_cinema_refresh.xml", """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="M19,12 A7,7 0 1,1 16.9,7.1"
        android:strokeColor="@color/molina_cinema_purple"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
    <path
        android:pathData="M17,3.5 L17,7.5 L13,7.5"
        android:strokeColor="@color/molina_cinema_purple"
        android:strokeWidth="1.8"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
""")

# --------------------------------------------------------------- string
sh = f"{P}/res/values/strings_home.xml"
sub(sh, "</resources>", """    <!-- molina-home-cinema -->
    <string name="home_cinema_title">Molina Cinema – Home Theater</string>
    <string name="home_brand">Molina</string>
    <string name="home_brand_sub">CINEMA · HOME THEATER</string>
    <string name="home_mode_auto">AUTO</string>
    <string name="home_input_hint">Cari di YouTube atau tempel tautan</string>
    <string name="home_help">Tempel tautan atau ketik judul di kolom atas, lalu tekan Putar. Ketuk Saluran untuk daftar TV (M3U).</string>
</resources>""", marker="home_cinema_title")

ss = f"{P}/res/values/strings.xml"
t = read(ss)
t2 = re.sub(r'\n[ \t]*<string name="chooser_folder(?:_deep)?(?:_sub)?">[^\n]*</string>', "", t)
t2 = re.sub(r'(<string name="chooser_file">)[^<]*(</string>)', r"\1Berkas dan folder\2", t2)
t2 = re.sub(r'(<string name="chooser_file_sub">)[^<]*(</string>)',
            r"\1Ketuk berkas untuk memutar; tekan lama folder untuk memutar isinya, subfolder ikut\2", t2)
t2 = re.sub(r'(<string name="hint_pick_folder_longpress">)[^<]*(</string>)',
            r"\1Ketuk berkas untuk memutar, atau tekan lama sebuah folder untuk memutar seluruh isinya (subfolder ikut)\2", t2)
if t2 != t:
    write(ss, t2)

# --------------------------------------------------------------- layout
write(f"{P}/res/layout/fragment_main_screen.xml", """<?xml version="1.0" encoding="utf-8"?>
<!-- molina-home-cinema: Home MPV gaya Home Theater (bingkai gradasi, 4 kartu menu cepat) -->
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/molina_cinema_bg"
    android:clipToPadding="false"
    android:fillViewport="true"
    android:overScrollMode="never">

    <LinearLayout
        android:id="@+id/content"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_margin="14dp"
        android:background="@drawable/bg_cinema_frame"
        android:orientation="vertical"
        android:padding="2dp">

        <!-- Baris judul kecil -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="36dp"
            android:gravity="center_vertical"
            android:orientation="horizontal"
            android:paddingHorizontal="14dp">

            <ImageView
                android:layout_width="18dp"
                android:layout_height="18dp"
                android:importantForAccessibility="no"
                android:src="@drawable/mpv_logo" />

            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_marginStart="9dp"
                android:layout_weight="1"
                android:ellipsize="end"
                android:maxLines="1"
                android:text="@string/home_cinema_title"
                android:textColor="@color/molina_cinema_text"
                android:textSize="14sp" />
        </LinearLayout>

        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:layout_marginHorizontal="8dp"
            android:background="@color/molina_cinema_green_dim" />

        <!-- Kotak isi (garis tipis di sekeliling) -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="0dp"
            android:layout_margin="8dp"
            android:layout_weight="1"
            android:background="@drawable/bg_cinema_box"
            android:orientation="vertical"
            android:padding="13dp">

            <!-- Identitas brand -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:layout_marginTop="8dp"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <ImageView
                    android:id="@+id/homeLogo"
                    android:layout_width="46dp"
                    android:layout_height="46dp"
                    android:importantForAccessibility="no"
                    android:src="@drawable/mpv_logo" />

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="19dp"
                    android:layout_weight="1"
                    android:orientation="vertical">

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:includeFontPadding="false"
                        android:text="@string/home_brand"
                        android:textColor="@color/molina_cinema_green"
                        android:textSize="26sp"
                        android:textStyle="bold" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="6dp"
                        android:letterSpacing="0.2"
                        android:maxLines="1"
                        android:text="@string/home_brand_sub"
                        android:textColor="@color/molina_cinema_blue"
                        android:textSize="11sp" />
                </LinearLayout>

                <TextView
                    android:id="@+id/homeModeBadge"
                    android:layout_width="49dp"
                    android:layout_height="31dp"
                    android:background="@drawable/bg_cinema_pill"
                    android:gravity="center"
                    android:letterSpacing="0.1"
                    android:text="@string/home_mode_auto"
                    android:textColor="@color/molina_cinema_green"
                    android:textSize="11sp"
                    android:textStyle="bold" />
            </LinearLayout>

            <!-- Kolom tautan / judul -->
            <EditText
                android:id="@+id/urlInput"
                android:layout_width="match_parent"
                android:layout_height="60dp"
                android:layout_marginTop="21dp"
                android:background="@drawable/bg_cinema_field"
                android:hint="@string/home_input_hint"
                android:imeOptions="actionGo"
                android:importantForAutofill="no"
                android:inputType="text|textNoSuggestions"
                android:maxLines="1"
                android:paddingHorizontal="16dp"
                android:singleLine="true"
                android:textColor="@color/molina_cinema_text"
                android:textColorHint="@color/molina_cinema_text2"
                android:textSize="14sp" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="9dp"
                android:orientation="horizontal">

                <Space
                    android:layout_width="0dp"
                    android:layout_height="0dp"
                    android:layout_weight="401" />

                <Button
                    android:id="@+id/pasteBtn"
                    android:layout_width="0dp"
                    android:layout_height="30dp"
                    android:layout_marginEnd="9dp"
                    android:layout_weight="227"
                    android:background="@drawable/bg_cinema_btn"
                    android:minWidth="0dp"
                    android:minHeight="0dp"
                    android:padding="0dp"
                    android:stateListAnimator="@null"
                    android:text="@string/home_paste"
                    android:textAllCaps="false"
                    android:textColor="@color/cinema_btn_text"
                    android:textSize="14sp" />

                <Button
                    android:id="@+id/playBtn"
                    android:layout_width="0dp"
                    android:layout_height="30dp"
                    android:layout_weight="227"
                    android:background="@drawable/bg_cinema_btn_primary"
                    android:minWidth="0dp"
                    android:minHeight="0dp"
                    android:padding="0dp"
                    android:stateListAnimator="@null"
                    android:text="@string/home_play"
                    android:textAllCaps="false"
                    android:textColor="@color/cinema_btn_text"
                    android:textSize="14sp" />
            </LinearLayout>

            <!-- 4 kartu menu cepat -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="70dp"
                android:layout_marginTop="14dp"
                android:orientation="horizontal">

                <LinearLayout
                    android:id="@+id/cardFiles"
                    android:layout_width="0dp"
                    android:layout_height="match_parent"
                    android:layout_marginEnd="9dp"
                    android:layout_weight="1"
                    android:background="@drawable/bg_cinema_card"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center"
                    android:orientation="vertical">

                    <ImageView
                        android:layout_width="24dp"
                        android:layout_height="24dp"
                        android:importantForAccessibility="no"
                        android:src="@drawable/ic_cinema_file" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:maxLines="1"
                        android:text="@string/home_menu_files"
                        android:textColor="@color/molina_cinema_text"
                        android:textSize="14sp" />
                </LinearLayout>

                <LinearLayout
                    android:id="@+id/cardChannels"
                    android:layout_width="0dp"
                    android:layout_height="match_parent"
                    android:layout_marginEnd="9dp"
                    android:layout_weight="1"
                    android:background="@drawable/bg_cinema_card"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center"
                    android:orientation="vertical">

                    <ImageView
                        android:layout_width="24dp"
                        android:layout_height="24dp"
                        android:importantForAccessibility="no"
                        android:src="@drawable/ic_cinema_tv" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:maxLines="1"
                        android:text="@string/home_menu_channels"
                        android:textColor="@color/molina_cinema_text"
                        android:textSize="14sp" />
                </LinearLayout>

                <LinearLayout
                    android:id="@+id/cardSettings"
                    android:layout_width="0dp"
                    android:layout_height="match_parent"
                    android:layout_marginEnd="9dp"
                    android:layout_weight="1"
                    android:background="@drawable/bg_cinema_card"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center"
                    android:orientation="vertical">

                    <ImageView
                        android:layout_width="24dp"
                        android:layout_height="24dp"
                        android:importantForAccessibility="no"
                        android:src="@drawable/ic_settings_black_24dp"
                        android:tint="@color/molina_cinema_purple" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:maxLines="1"
                        android:text="@string/home_menu_settings"
                        android:textColor="@color/molina_cinema_text"
                        android:textSize="14sp" />
                </LinearLayout>

                <LinearLayout
                    android:id="@+id/cardRefresh"
                    android:layout_width="0dp"
                    android:layout_height="match_parent"
                    android:layout_weight="1"
                    android:background="@drawable/bg_cinema_card"
                    android:clickable="true"
                    android:focusable="true"
                    android:gravity="center"
                    android:orientation="vertical">

                    <ImageView
                        android:layout_width="24dp"
                        android:layout_height="24dp"
                        android:importantForAccessibility="no"
                        android:src="@drawable/ic_cinema_refresh" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:layout_marginTop="8dp"
                        android:maxLines="1"
                        android:text="@string/home_menu_refresh"
                        android:textColor="@color/molina_cinema_text"
                        android:textSize="14sp" />
                </LinearLayout>
            </LinearLayout>

            <!-- Terakhir diputar -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="18dp"
                android:gravity="center_vertical"
                android:orientation="horizontal">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/home_recent_title"
                    android:textColor="@color/molina_cinema_blue"
                    android:textSize="15sp" />

                <TextView
                    android:id="@+id/recentClearBtn"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:background="@drawable/bg_home_row"
                    android:clickable="true"
                    android:focusable="true"
                    android:paddingHorizontal="10dp"
                    android:paddingVertical="6dp"
                    android:text="@string/home_recent_clear"
                    android:textColor="@color/molina_cinema_text2"
                    android:textSize="13sp"
                    android:visibility="gone" />
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:layout_marginTop="8dp"
                android:background="@color/molina_cinema_box_line" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="0dp"
                android:layout_weight="1"
                android:minHeight="96dp"
                android:orientation="vertical">

                <LinearLayout
                    android:id="@+id/recentList"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:orientation="vertical" />

                <TextView
                    android:id="@+id/recentEmpty"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:paddingTop="14dp"
                    android:text="@string/home_recent_empty"
                    android:textColor="@color/molina_cinema_text2"
                    android:textSize="14sp" />
            </LinearLayout>

            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:background="@color/molina_cinema_box_line" />

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:lineSpacingExtra="5dp"
                android:text="@string/home_help"
                android:textColor="@color/molina_cinema_text2"
                android:textSize="14sp" />

            <androidx.appcompat.widget.SwitchCompat
                android:id="@+id/switch1"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:text="@string/option_remember_choice"
                android:textColor="@color/molina_cinema_text2"
                android:textSize="12sp" />
        </LinearLayout>
    </LinearLayout>
</ScrollView>
""")

# ------------------------------------------------- Kotlin: HomeController
hc = f"{P}/java/com/molina/player/home/HomeController.kt"
sub(hc, """        b.homeMenuBtn.setOnClickListener { anchor ->
            Diag.behavior("player", "home-menu-open", "menu Home dibuka")
            HomeMenu.show(anchor) { onAction(it) }
        }
""", """        b.cardFiles.setOnClickListener { onAction(HomeAction.FILES) }
        b.cardChannels.setOnClickListener { onAction(HomeAction.CHANNELS) }
        b.cardSettings.setOnClickListener { onAction(HomeAction.SETTINGS) }
        b.cardRefresh.setOnClickListener { onAction(HomeAction.REFRESH) }
""", marker="b.cardFiles.setOnClickListener")

# --------------------------------------------- Kotlin: OpenChooserSheet
oc = f"{P}/java/com/molina/player/OpenChooserSheet.kt"
sub(oc, "enum class Choice { FILE, FOLDER, FOLDER_DEEP, SYSTEM }", "enum class Choice { LOCAL, SYSTEM }",
    marker="enum class Choice { LOCAL")
sub(oc, """        Option(Choice.FILE, R.drawable.ic_file_open_48dp, R.string.chooser_file, R.string.chooser_file_sub),
        Option(Choice.FOLDER, R.drawable.ic_folder_white_48dp, R.string.chooser_folder, R.string.chooser_folder_sub),
        Option(Choice.FOLDER_DEEP, R.drawable.ic_folders_48dp, R.string.chooser_folder_deep, R.string.chooser_folder_deep_sub),
""", """        Option(Choice.LOCAL, R.drawable.ic_file_open_48dp, R.string.chooser_file, R.string.chooser_file_sub),
""", marker="Option(Choice.LOCAL")
sub(oc, " * Dialog pilihan tombol \"Buka dari perangkat\" di Home: berkas, folder, folder dalam folder,\n * dan pemilih sistem (SAF) untuk penyimpanan tanpa izin berkas.",
    " * Dialog kartu Berkas di Home: satu pemilih untuk berkas dan folder (subfolder ikut),\n * dan pemilih sistem (SAF) untuk penyimpanan tanpa izin berkas.",
    marker="satu pemilih untuk berkas dan folder")

# ------------------------------------------- Kotlin: MainScreenFragment
mf = f"{P}/java/com/molina/player/MainScreenFragment.kt"
sub(mf, " * Beranda Molina Player (gaya panel Linux Mint): satu kolom untuk tautan ATAU judul (dicari di YouTube),\n * satu tombol \"Buka dari perangkat\" (berkas, folder, folder dalam folder, pemilih sistem), dan Setelan.",
    " * Beranda Molina Cinema (Home Theater): satu kolom untuk tautan ATAU judul (dicari di YouTube),\n * empat kartu (Berkas, Saluran, Setelan, Segarkan); Berkas membuka satu pemilih untuk berkas dan folder\n * (subfolder ikut) atau pemilih sistem.",
    marker="Beranda Molina Cinema")
sub(mf, """    /** Mode pemilih berkas yang sedang terbuka; menentukan arti folder yang dipilih. */
    private var pickMode = PickMode.FILE

    private enum class PickMode(val key: String) {
        FILE("file"), FOLDER("folder"), DEEP("deep");

        companion object {
            fun of(key: String?): PickMode? = values().firstOrNull { it.key == key }
        }
    }

""", "")
sub(mf, "        pickMode = PickMode.of(savedInstanceState?.getString(STATE_PICK_MODE)) ?: PickMode.FILE\n", "")
sub(mf, "binding.homeMenuBtn.setOnLongClickListener { showDebugMenu(); true }",
    "binding.homeLogo.setOnLongClickListener { showDebugMenu(); true }", marker="binding.homeLogo.setOnLongClickListener")
sub(mf, """    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PICK_MODE, pickMode.key)
    }

""", "")
sub(mf, """                OpenChooserSheet.Choice.FILE -> launchPicker(PickMode.FILE)
                OpenChooserSheet.Choice.FOLDER -> launchPicker(PickMode.FOLDER)
                OpenChooserSheet.Choice.FOLDER_DEEP -> launchPicker(PickMode.DEEP)
""", """                OpenChooserSheet.Choice.LOCAL -> launchPicker()
""", marker="Choice.LOCAL -> launchPicker()")
sub(mf, """    private fun launchPicker(mode: PickMode) {
        saveChoice(mode.key)
        pickMode = mode
        if (mode != PickMode.FILE) {
            // pemilih lama memilih folder lewat tekan-lama; beri petunjuk agar tidak membingungkan
            Toast.makeText(requireContext(), R.string.hint_pick_folder_longpress, Toast.LENGTH_LONG).show()
        }
        val i = Intent(context, FilePickerActivity::class.java)
        i.putExtra("skip", FilePickerActivity.FILE_PICKER)
        i.putExtra("allow_dir", mode != PickMode.FILE)
""", """    /** Satu pemilih untuk berkas dan folder: berkas diputar langsung, folder (tekan lama) jadi playlist beserta subfolder. */
    private fun launchPicker() {
        saveChoice(KEY_OPEN)
        Diag.behavior("player", "open-local", "pemilih berkas dibuka")
        showPickHintOnce()
        val i = Intent(context, FilePickerActivity::class.java)
        i.putExtra("skip", FilePickerActivity.FILE_PICKER)
        i.putExtra("allow_dir", true)
""", marker="private fun launchPicker()")
sub(mf, "    private fun launchDocTree() {", """    /** Petunjuk tekan-lama folder tampil sekali saja, bukan setiap kali pemilih dibuka. */
    private fun showPickHintOnce() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        if (prefs.getBoolean(KEY_PICK_HINT_SEEN, false)) return
        prefs.edit().putBoolean(KEY_PICK_HINT_SEEN, true).apply()
        Toast.makeText(requireContext(), R.string.hint_pick_folder_longpress, Toast.LENGTH_LONG).show()
    }

    private fun launchDocTree() {""", marker="private fun showPickHintOnce")
sub(mf, """        if (pickMode != PickMode.FILE && f.isDirectory) {
            playFolder(f, deep = pickMode == PickMode.DEEP)
        } else {""", """        if (f.isDirectory) {
            Diag.behavior("player", "open-local-folder", "folder dipilih (subfolder ikut)")
            playFolder(f, deep = true)
        } else {""", marker='"open-local-folder"')
sub(mf, "            else -> PickMode.of(type)?.let { launchPicker(it) }",
    '            KEY_OPEN, "file", "folder", "deep" -> launchPicker() // tiga nilai terakhir: simpanan lama sebelum digabung',
    marker="KEY_OPEN, \"file\"")
sub(mf, '        private const val STATE_PICK_MODE = "pick_mode"\n',
    '        private const val KEY_OPEN = "open"\n        private const val KEY_PICK_HINT_SEEN = "molina_pick_hint_seen"\n',
    marker="KEY_PICK_HINT_SEEN = ")

# ------------------------------------------------------ docs/LOG_CODES
lc = "docs/LOG_CODES.md"
t = read(lc)
t2 = t.replace("| `player.home-menu-open` | BEHAV | player/src/main/java/com/molina/player/home/HomeController.kt |\n", "")
row_menu = "| `player.menu-empty` | BEHAV | player/src/main/java/com/molina/player/menu/MPVActivityMenu.kt |\n"
new_rows = ("| `player.open-local` | BEHAV | player/src/main/java/com/molina/player/MainScreenFragment.kt |\n"
            "| `player.open-local-folder` | BEHAV | player/src/main/java/com/molina/player/MainScreenFragment.kt |\n")
if "`player.open-local`" not in t2:
    if row_menu not in t2:
        sys.exit("GAGAL: baris player.menu-empty tidak ada di docs/LOG_CODES.md")
    t2 = t2.replace(row_menu, row_menu + new_rows, 1)
if t2 != t:
    write(lc, t2)

# --------------------------------------------------------- verifikasi
bad = []
kt = read(mf)
for tok in ("PickMode", "pickMode", "STATE_PICK_MODE", "homeMenuBtn"):
    if tok in kt:
        bad.append(f"{tok} masih ada di MainScreenFragment.kt")
if "homeMenuBtn" in read(hc):
    bad.append("homeMenuBtn masih ada di HomeController.kt")
if re.search(r"chooser_folder", read(ss)):
    bad.append("string chooser_folder* masih ada")
if bad:
    sys.exit("GAGAL verifikasi: " + "; ".join(bad))

print("Berubah:" if changed else "Tidak ada perubahan (sudah terpasang).")
for c in changed:
    print("  *", c)
PYEOF
