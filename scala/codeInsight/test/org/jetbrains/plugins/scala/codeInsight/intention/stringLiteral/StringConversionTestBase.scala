package org.jetbrains.plugins.scala.codeInsight.intention.stringLiteral

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInsight.intentions
import org.jetbrains.plugins.scala.extensions.executeWriteActionCommand
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.jdk.CollectionConverters.CollectionHasAsScala

abstract class StringConversionTestBase extends intentions.ScalaIntentionTestBase  {

  override def normalize(text: String): String =
    text.replace("\r", "")

  // NOTE: current implementation only works when each intention action does not adds new lines or removes any lines
  protected def doBulkTest(
    text: String,
    resultText: String,
    fileType: FileType = fileType
  ): Unit = {
    implicit val project: Project = getProject

    myFixture.configureByText(fileType, normalize(text)).asInstanceOf[ScalaFile]

    placeCaretAtEachLineContent(getEditor)

    val caretModel  = getEditor.getCaretModel

    val carets = caretModel.getAllCarets.asScala.toSeq.map(_.getVisualPosition)
    carets.foreach { caret =>
      caretModel.getCurrentCaret.moveToVisualPosition(caret)

      val intention = findIntentionByName(familyName)
      intention.foreach { action =>
        executeWriteActionCommand("Invoke Intention Action") {
          action.invoke(project, getEditor, getFile)
        }
      }
    }

    checkIntentionResultText(resultText)(text)
  }

  /**
   * {{{
   * class A {
   *   2 + 2
   * }
   * }}}
   * ->
   * {{{
   * <caret>class A {
   *   <caret>2 + 2
   * <caret>}
   * }}}
   */
  private def placeCaretAtEachLineContent(editor: Editor): Unit = {
    val document = editor.getDocument
    val caretModel = editor.getCaretModel
    val text = document.getText

    // place a caret at the beginning of content on each line
    (0 until document.getLineCount).foreach { line =>
      var contentOnLineOffset = document.getLineStartOffset(line)
      while (contentOnLineOffset < text.length && text.charAt(contentOnLineOffset).isWhitespace)
        contentOnLineOffset += 1

      caretModel.addCaret(editor.offsetToVisualPosition(contentOnLineOffset))
    }
  }
}
