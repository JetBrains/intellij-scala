package org.jetbrains.plugins.scala.annotator.element

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import junit.framework.{Test, TestCase}
import org.jetbrains.plugins.scala.annotator.AnnotatorHolderMockBase
import org.jetbrains.plugins.scala.base.ScalaFileSetTestCase
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral

import scala.annotation.nowarn
import scala.math.Ordered.orderingToOrdered

class ScStringLiteralAnnotatorTest extends TestCase

object ScStringLiteralAnnotatorTest {

  def suite: Test = new ScalaFileSetTestCase("/annotator/string_literals/") {
    override protected def transform(testName: String, fileText: String, project: Project): String = {
      val lightFile = createLightFile(fileText, project)

      val messages = collectMessages(lightFile)

      messages.mkString("\n")
    }

    private def collectMessages(file: PsiFile): List[MyMessage] = {
      val mock = new MyAnnotatorHolderMock(file)

      val literals = file.depthFirst().filterByType[ScStringLiteral].toSeq
      literals.foreach(ElementAnnotator.annotate(_)(mock))

      mock.annotations
    }
  }

  implicit private object TextRangeOrdering extends scala.math.Ordering[TextRange] {
    override def compare(x: TextRange, y: TextRange): Int =
      (x.getStartOffset, x.getEndOffset) compare (y.getStartOffset, y.getEndOffset)
  }

  // NOTE: we could try to unify with org.jetbrains.plugins.scala.annotator.Message
  // which currently doesn't test text ranges, but only test file text (it's has it's advantages and disadvantages)
  sealed abstract class MyMessage extends Ordered[MyMessage] {
    def range: TextRange
    def message: String

    override def compare(that: MyMessage): Int =
      (this.range, this.message) compare (that.range, that.message)
  }
  object MyMessage {
    case class Info(override val range: TextRange, override val message: String) extends MyMessage
    case class Warning(override val range: TextRange, override val message: String) extends MyMessage
    case class Error(override val range: TextRange, override val message: String) extends MyMessage
  }

  class MyAnnotatorHolderMock(file: PsiFile) extends AnnotatorHolderMockBase[MyMessage](file) {

    //noinspection ScalaUnnecessaryParentheses
    @nowarn("cat=deprecation")
    override def createMockAnnotation(s: HighlightSeverity, range: TextRange, message: String): Option[MyMessage] =
      s match {
        case HighlightSeverity.ERROR        => Some(MyMessage.Error(range, message))
        case HighlightSeverity.WARNING |
             HighlightSeverity.GENERIC_SERVER_ERROR_OR_WARNING |
             HighlightSeverity.WEAK_WARNING => Some(MyMessage.Warning(range, message))
        case HighlightSeverity.INFORMATION |
             (HighlightSeverity.INFO)         => Some(MyMessage.Info(range, message))
        case _                              => None
      }
  }
}