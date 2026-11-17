package com.molina.code

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.molina.code.ui.CodeScreen

/** Tab Code: menampung editor Compose. Sesi hidup di [CodeSessionHolder] sehingga pindah tab tidak menutup tab editor. */
class CodeFragment : Fragment() {
    private val holder: CodeSessionHolder by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { CodeScreen(holder.session, Modifier.fillMaxSize()) }
        }

    override fun onResume() {
        super.onResume()
        holder.session.checkFolderChanges()
    }

    override fun onStop() {
        holder.session.flushDrafts()
        super.onStop()
    }
}
