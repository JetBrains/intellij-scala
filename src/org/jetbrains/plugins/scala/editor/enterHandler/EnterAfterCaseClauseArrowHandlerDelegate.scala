package org.jetbrains.plugins.scala
package editor
package enterHandler

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import java.lang.Integer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import lang.psi.api.base.patterns.{ScCaseClauses, ScCaseClause}
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.codeStyle.CodeStyleSettingsManager

/**
 * Handler for `case a => <enter>`.
 */
class EnterAfterCaseClauseArrowHandlerDelegate extends EnterHandlerDelegate {
  def preprocessEnter(file: PsiFile, editor: Editor, caretOffsetRef: Ref[Integer], caretAdvance: Ref[Integer],
                      dataContext: DataContext, originalHandler: EditorActionHandler): Result = {
    val styleSettings = CodeStyleSettingsManager.getSettings(file.getProject())
    val isScalaFile = file.getFileType == ScalaFileType.SCALA_FILE_TYPE
    val isSmartIndent = CodeInsightSettings.getInstance().SMART_INDENT_ON_ENTER
    val enabled = isScalaFile && isSmartIndent && styleSettings.INDENT_CASE_FROM_SWITCH

    if (enabled) {
      val document = editor.getDocument
      val text = document.getCharsSequence
      val caretOffset = caretOffsetRef.get.intValue
      val element = file.findElementAt(caretOffset)
      if (element != null) {
        element.getPrevSibling match {
          case _: ScCaseClause  | _ : ScCaseClauses =>
            val indentSize = styleSettings.getIndentOptions(ScalaFileType.SCALA_FILE_TYPE).INDENT_SIZE
            caretAdvance.set(indentSize)
            return Result.DefaultForceIndent
          case _ =>
        }
      }
    }
    Result.Continue
  }
}