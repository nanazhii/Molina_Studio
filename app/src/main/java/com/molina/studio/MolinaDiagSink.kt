package com.molina.studio

import com.molina.core.common.diag.DiagSink
import com.molina.log.MolinaEvents

/** Satu-satunya perekat kontrak Diag (core-common) ke MolinaEvents (core-log). */
object MolinaDiagSink : DiagSink {
    override fun behavior(area: String, code: String, summary: String, fields: Map<String, String>) {
        MolinaEvents.behavior(area, code, summary, fields)
    }

    override fun bug(area: String, code: String, summary: String, error: Throwable?, fields: Map<String, String>) {
        MolinaEvents.bug(area, code, summary, error, fields)
    }
}
