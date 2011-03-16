package org.jetbrains.plugins.scala.conversion
package copy

import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.{RangeMarker, Editor}
import com.intellij.codeInsight.editorActions.{TextBlockTransferableData, CopyPastePostProcessor}
import java.awt.datatransfer.{DataFlavor, Transferable}
import collection.mutable.ArrayBuffer
import com.intellij.psi.{PsiDocumentManager, PsiJavaFile, PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import com.intellij.psi.codeStyle.{CodeStyleSettingsManager, CodeStyleManager}
import java.lang.Boolean
import com.intellij.openapi.util.Ref
import org.jetbrains.plugins.scala.extensions._

/**
 * User: Alexander Podkhalyuzin
 * Date: 30.11.2009
 */

class JavaCopyPastePostProcessor extends CopyPastePostProcessor[TextBlockTransferableData] {
  def collectTransferableData(file: PsiFile, editor: Editor, startOffsets: Array[Int], endOffsets: Array[Int]): TextBlockTransferableData = {
    val settings = CodeStyleSettingsManager.getSettings(file.getProject)
            .getCustomSettings(classOf[ScalaCodeStyleSettings])

    if (!settings.ENABLE_JAVA_TO_SCALA_CONVERSION || !file.isInstanceOf[PsiJavaFile]) return null

    val buffer = new ArrayBuffer[PsiElement]

    try {
      for ((startOffset, endOffset) <- startOffsets.zip(endOffsets)) {
        var elem: PsiElement = file.findElementAt(startOffset)
        while (elem != null && elem.getParent != null && !elem.getParent.isInstanceOf[PsiFile] &&
                elem.getParent.getTextRange.getEndOffset <= endOffset) {
          elem = elem.getParent
        }
        buffer += elem
        while (elem.getTextRange.getEndOffset < endOffset) {
          elem = elem.getNextSibling
          buffer += elem
        }
      }
      val newText = JavaToScala.convertPsiToText(buffer.toArray)
      new StringTransferableData(newText)
    } catch {
      case _ => null
    }
  }

  def extractTransferableData(content: Transferable): TextBlockTransferableData = {
    if (content.isDataFlavorSupported(StringTransferableData.flavor))
      content.getTransferData(StringTransferableData.flavor).asInstanceOf[TextBlockTransferableData]
    else
      null
  }

  def processTransferableData(project: Project, editor: Editor, bounds: RangeMarker, i: Int, ref: Ref[Boolean], value: TextBlockTransferableData) {
    val settings = CodeStyleSettingsManager.getSettings(project)
    val scalaSettings: ScalaCodeStyleSettings = settings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    if (!scalaSettings.ENABLE_JAVA_TO_SCALA_CONVERSION) return
    if (value == null) return
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument)
    if (!file.isInstanceOf[ScalaFile]) return
    val dialog = new ScalaPasteFromJavaDialog(project)
    val text = value match {
      case s: StringTransferableData => s.data
      case _ => ""
    }
    if (text == "") return //copy as usually
    if (!scalaSettings.DONT_SHOW_CONVERSION_DIALOG) dialog.show()
    if (scalaSettings.DONT_SHOW_CONVERSION_DIALOG || dialog.isOK) {
      inWriteAction {
        editor.getDocument.replaceString(bounds.getStartOffset, bounds.getEndOffset, text)
        editor.getCaretModel.moveToOffset(bounds.getStartOffset + text.length)
        PsiDocumentManager.getInstance(file.getProject).commitDocument(editor.getDocument)
        val manager: CodeStyleManager = CodeStyleManager.getInstance(project)
        val keep_blank_lines_in_code = settings.KEEP_BLANK_LINES_IN_CODE
        val keep_blank_lines_in_declarations = settings.KEEP_BLANK_LINES_IN_DECLARATIONS
        val keep_blank_lines_before_rbrace = settings.KEEP_BLANK_LINES_BEFORE_RBRACE
        settings.KEEP_BLANK_LINES_IN_CODE = 0
        settings.KEEP_BLANK_LINES_IN_DECLARATIONS = 0
        settings.KEEP_BLANK_LINES_BEFORE_RBRACE = 0
        manager.reformatText(file, bounds.getStartOffset, bounds.getStartOffset + text.length)
        settings.KEEP_BLANK_LINES_IN_CODE = keep_blank_lines_in_code
        settings.KEEP_BLANK_LINES_IN_DECLARATIONS = keep_blank_lines_in_declarations
        settings.KEEP_BLANK_LINES_BEFORE_RBRACE = keep_blank_lines_before_rbrace
      }
    }
  }

  class StringTransferableData(val data: String) extends TextBlockTransferableData {
    def setOffsets(offsets: Array[Int], index: Int): Int = 0
    def getOffsets(offsets: Array[Int], index: Int): Int = 0
    def getOffsetCount: Int = 0
    def getFlavor: DataFlavor = StringTransferableData.flavor
  }

  object StringTransferableData {
    val flavor: DataFlavor = new DataFlavor(classOf[JavaCopyPastePostProcessor], "class: ScalaCopyPastePostProcessor")
  }
}