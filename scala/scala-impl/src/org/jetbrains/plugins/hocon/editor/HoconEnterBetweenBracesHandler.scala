package org.jetbrains.plugins.hocon.editor

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.{EnterBetweenBracesHandler, EnterHandlerDelegate}
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.hocon.lang.HoconLanguage

/**
  * Like [[com.intellij.json.formatter.JsonEnterBetweenBracesHandler]]
  *
  * @author ghik
  */
class HoconEnterBetweenBracesHandler extends EnterBetweenBracesHandler {
  override def preprocessEnter(file: PsiFile, editor: Editor, caretOffsetRef: Ref[Integer],
                               caretAdvance: Ref[Integer], dataContext: DataContext,
                               originalHandler: EditorActionHandler): Result =
    if (file.getLanguage is HoconLanguage)
      super.preprocessEnter(file, editor, caretOffsetRef, caretAdvance, dataContext, originalHandler)
    else
      EnterHandlerDelegate.Result.Continue

  override def isBracePair(c1: Char, c2: Char): Boolean =
    c1 == '{' && c2 == '}' || c1 == '[' && c2 == ']'
}
