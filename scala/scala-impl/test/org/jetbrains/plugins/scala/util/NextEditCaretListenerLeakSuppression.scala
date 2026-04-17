package org.jetbrains.plugins.scala.util

import com.intellij.util.lang.CompoundRuntimeException

import scala.jdk.CollectionConverters._

/**
 * Suppresses a specific `CaretListener` leak that originates in the platform
 * component `NextEditCaretFeatures` (AI Assistant / ml-llm-nextEdits plugin).
 * Not our bug — do not expand this to suppress unrelated leaks.
 *
 * TODO: Periodically try removing this object and its call sites once the
 *       upstream platform leak in `NextEditCaretFeatures` is fixed.
 */
object NextEditCaretListenerLeakSuppression {

  private final val ListenerInterfaceMarker =
    "Listeners leaked for interface com.intellij.openapi.editor.event.CaretListener"
  private final val LeakingComponentMarker =
    "com.intellij.ml.llm.nextEdits.backend.logs.statistics.components.NextEditCaretFeatures"

  def isKnownLeak(t: Throwable): Boolean = t match {
    case ae: AssertionError =>
      val m = ae.getMessage
      m != null && m.contains(ListenerInterfaceMarker) && m.contains(LeakingComponentMarker)
    case c: CompoundRuntimeException =>
      val es = c.getExceptions.asScala
      es.nonEmpty && es.forall(isKnownLeak)
    case _ => false
  }

  def runSuppressing(block: => Unit): Unit = {
    try block
    catch {
      case t: Throwable if isKnownLeak(t) => // intentionally suppressed
    }
  }
}
