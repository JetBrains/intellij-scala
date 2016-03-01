package org.jetbrains.plugins.scala.base

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.lang.annotation.HighlightSeverity
import org.intellij.lang.annotations.Language
import org.junit.Assert

import scala.collection.JavaConverters._
import scala.reflect.ClassTag

/**
  * @author Pavel Fatin
  */
abstract class InspectionTestCase[T <: LocalInspectionTool : ClassTag] extends SimpleTestCase {
  protected def assertHighlights(@Language("Scala") code: String, highlights: Highlight*) {
    Assert.assertEquals(highlights, highlight(code, implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]))
  }

  protected def highlight(@Language("Scala") code: String, inspection: Class[_ <: LocalInspectionTool]): Seq[Highlight] = {
    fixture.configureByText("dummy.scala", code)
    fixture.enableInspections(inspection)

    fixture.doHighlighting().asScala.flatMap { it =>
      val severity = it.getSeverity match {
        case HighlightSeverity.ERROR => Error
        case HighlightSeverity.WARNING => Warning
        case HighlightSeverity.WEAK_WARNING => WeakWarning
        case _ => Information
      }
      if (severity == Information) Seq.empty
      else Seq(Highlight(it.getStartOffset, it.getEndOffset, it.getDescription, severity))
    }
  }

  protected sealed trait Severity

  protected case object Error extends Severity

  protected case object Warning extends Severity

  protected case object WeakWarning extends Severity

  protected case object Information extends Severity

  protected case class Highlight(begin: Int, end: Int, description: String, severity: Severity = Warning)
}
