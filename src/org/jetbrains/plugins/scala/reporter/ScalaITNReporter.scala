package org.jetbrains.plugins.scala.reporter

import com.intellij.diagnostic.ITNReporter
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.diagnostic.{SubmittedReportInfo, IdeaLoggingEvent}
import java.awt.Component
import org.jetbrains.plugins.scala.util.ScalaUtils

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.12.2009
 */

class ScalaITNReporter extends ITNReporter {
  override def submit(events: Array[IdeaLoggingEvent], parentComponent: Component): SubmittedReportInfo = {
    super.submit(Array(new IdeaLoggingEvent("Scala Plugin Version: " + ScalaUtils.getPluginVersion + "\n\n" +
            events(0).getMessage, events(0).getThrowable)), parentComponent)
  }
}