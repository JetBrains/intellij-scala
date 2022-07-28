package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.TypecheckerTests
import org.jetbrains.plugins.scala.annotator.hints.AnnotatorHints
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, StringExt}
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions
import org.junit.Assert.fail
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
abstract class ScalaHighlightingTestBase extends ScalaFixtureTestCase with MatcherAssertions {

  private var filesCreated: Boolean = false

  protected def withHints = false

  def assertNoErrors(code: String): Unit =
    assertErrors(code, Nil: _*)

  def assertErrors(code: String, messages: Message*): Unit =
    assertErrorsText(code, messages.mkString("\n"))

  def assertErrorsText(code: String, messagesConcatenated: String): Unit = {
    // handle windows '\r', ignore empty lines
    val messagesConcatenatedClean =
      messagesConcatenated.withNormalizedSeparator.replaceAll("\\n\\n+", "\n").trim

    val actualMessages = errorsFromScalaCode(code)
    val actualMessagesConcatenated = actualMessages.mkString("\n")
    assertEqualsFailable(
      messagesConcatenatedClean,
      actualMessagesConcatenated
    )
  }

  def errorsFromScalaCode(scalaFileText: String): List[Message] = {
    val fileName = s"dummy.scala"
    errorsFromScalaCode(scalaFileText, fileName)
  }

  def errorsFromScalaCode(scalaFileText: String, fileName: String): List[Message] = {
    if (filesCreated)
      fail("Don't add files 2 times in a single test")

    myFixture.configureByText(fileName, scalaFileText)

    filesCreated = true

    errorsFromScalaCode(getFile)
  }

  def errorsFromScalaCode(file: PsiFile): List[Message] = {
    PsiDocumentManager.getInstance(getProject).commitAllDocuments()

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file.depthFirst().foreach(annotate(_))

    val messages = mock.annotations.filter {
      case Error(_, null) | Error(null, _) => false
      case Error(_, _) => true
      case _ => false
    }

    if (withHints) {
      // TODO allow to check prefix / suffix, text attributes, error tooltip
      val hints = file.elements
        .flatMap(AnnotatorHints.in(_).toSeq.flatMap(_.hints))
        .map(hint => Hint(hint.element.getText, hint.parts.map(_.string).mkString, offsetDelta = hint.offsetDelta)).toList
      hints ::: messages
    } else {
      messages
    }
  }

  def annotate(element: PsiElement)
              (implicit holder: ScalaAnnotationHolder): Unit =
    ScalaAnnotator.forProject.annotate(element)
}
