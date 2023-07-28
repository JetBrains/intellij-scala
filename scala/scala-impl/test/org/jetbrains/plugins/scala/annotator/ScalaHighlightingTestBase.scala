package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.TypecheckerTests
import org.jetbrains.plugins.scala.annotator.hints.AnnotatorHints
import org.jetbrains.plugins.scala.base.ScalaFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiElementExt, StringExt}
import org.jetbrains.plugins.scala.util.assertions.MatcherAssertions
import org.junit.Assert.fail
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
abstract class ScalaHighlightingTestBase extends ScalaFixtureTestCase with MatcherAssertions {
  import Message._

  private var filesCreated: Boolean = false

  def assertNoErrors(code: String): Unit =
    assertErrors(code, Nil: _*)

  def assertErrors(code: String, messages: Message*): Unit =
    assertErrorsText(code, messages.mkString("\n"))

  def assertErrorsWithHints(code: String, messages: Message*): Unit =
    assertErrorsWithHintsText(code, messages.mkString("\n"))

  def assertMessages(code: String, messages: Message*): Unit =
    assertMessagesText(code, messages.mkString("\n"))

  def assertNoMessages(code: String): Unit =
    assertMessages(code, Nil: _*)

  def assertErrorsText(code: String, messagesConcatenated: String): Unit = {
    val actualMessages = errorsFromScalaCode(code)
    assertMessagesTextImpl(messagesConcatenated, actualMessages)
  }

  def assertErrorsWithHintsText(code: String, messagesConcatenated: String): Unit = {
    val actualMessages = errorsWithHintsFromScalaCode(code)
    assertMessagesTextImpl(messagesConcatenated, actualMessages)
  }

  def assertMessagesText(code: String, messagesConcatenated: String): Unit = {
    val actualMessages = messagesFromScalaCode(code)
    assertMessagesTextImpl(messagesConcatenated, actualMessages)
  }

  private def assertMessagesTextImpl(
    expectedMessagesConcatenated: String,
    actualMessages: Seq[Message],
  ): Unit = {
    // handle windows '\r', ignore empty lines
    val messagesConcatenatedClean =
      expectedMessagesConcatenated.withNormalizedSeparator.replaceAll("\\n\\n+", "\n").trim

    val actualMessagesConcatenated = actualMessages.mkString("\n")
    assertEqualsFailable(
      messagesConcatenatedClean,
      actualMessagesConcatenated
    )
  }

  def errorsFromScalaCode(scalaFileText: String): List[Error] =
    errorsFromScalaCode(scalaFileText, s"dummy.scala")

  def errorsWithHintsFromScalaCode(scalaFileText: String): List[Message] =
    errorsWithHintsFromScalaCode(scalaFileText, s"dummy.scala")

  def messagesFromScalaCode(scalaFileText: String): List[Message] =
    messagesFromScalaCode(scalaFileText, s"dummy.scala")

  def errorsFromScalaCode(scalaFileText: String, fileName: String): List[Error] = {
    createFile(scalaFileText, fileName)
    errorsFromScalaCode(getFile)
  }

  def errorsWithHintsFromScalaCode(scalaFileText: String, fileName: String): List[Message] = {
    createFile(scalaFileText, fileName)
    errorsWithHintsFromScalaCode(getFile)
  }

  def messagesFromScalaCode(scalaFileText: String, fileName: String): List[Message] = {
    createFile(scalaFileText, fileName)
    messagesFromScalaCode(getFile)
  }

  private def createFile(scalaFileText: String, fileName: String): Unit = {
    if (filesCreated)
      fail("Don't add files 2 times in a single test")

    myFixture.configureByText(fileName, scalaFileText)

    filesCreated = true
  }

  def errorsFromScalaCode(file: PsiFile): List[Error] =
    nonEmptyMessagesFromScalaCode(file).filterByType[Error]

  def errorsWithHintsFromScalaCode(file: PsiFile): List[Message] = {
    val errors = nonEmptyMessagesFromScalaCode(file).filterByType[Error]

    val hints = file.elements
      .flatMap(AnnotatorHints.in(_).toSeq.flatMap(_.hints))
      .map(hint => Hint(hint.element.getText, hint.parts.map(_.string).mkString, offsetDelta = hint.offsetDelta)).toList

    hints ::: errors
  }

  private def nonEmptyMessagesFromScalaCode(file: PsiFile): List[Message] =
    messagesFromScalaCode(file).filter(m => m.element != null && m.message != null)

  private def messagesFromScalaCode(file: PsiFile): List[Message] = {
    PsiDocumentManager.getInstance(getProject).commitAllDocuments()

    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)

    file.depthFirst().foreach(annotate(_))

    mock.annotations
  }

  def annotate(element: PsiElement)
              (implicit holder: ScalaAnnotationHolder): Unit =
    new ScalaAnnotator().annotate(element)
}
