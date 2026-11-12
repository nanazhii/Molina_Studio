package com.molina.core.ui

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.annotation.StyleRes

/**
 * Lingkup tema per tab tanpa mengganti tema Activity.
 * Contoh di Fragment: inflater.cloneInContext(requireContext().molinaThemed(R.style.Theme_Molina_Cinema))
 */
fun Context.molinaThemed(@StyleRes themeRes: Int): Context = ContextThemeWrapper(this, themeRes)
