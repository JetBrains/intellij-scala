package org.jetbrains.plugins.hocon.editor

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.plugins.hocon.CommonUtil.notWhiteSpaceSibling
import org.jetbrains.plugins.hocon.lexer.HoconTokenType
import org.jetbrains.plugins.hocon.psi.HoconPsiFile

/**
  * HOCON line comments can start with either '//' or '#'. Unfortunately, only one of them can be declared in
  * [[HoconCommenter]] and so I need this custom enter handler for the other one.
  */
class EnterInHashCommentHandler extends EnterHandlerDelegateAdapter {
  override def preprocessEnter(file: PsiFile, editor: Editor, caretOffsetRef: Ref[Integer], caretAdvance: Ref[Integer],
                               dataContext: DataContext, originalHandler: EditorActionHandler): Result =
    file match {
      case _: HoconPsiFile =>
        // This code is copied from com.intellij.codeInsight.editorActions.enter.EnterInLineCommentHandler
        // References to data from language commenter (line comment element type and prefix) have been replaced
        // with hardcoded values - HoconTokenType.HashComment and '#'
        val caretOffset = caretOffsetRef.get.intValue
        val psiAtOffset = file.findElementAt(caretOffset)
        if (psiAtOffset != null && psiAtOffset.getTextOffset < caretOffset) {
          val token = psiAtOffset.getNode
          val document = editor.getDocument
          val text = document.getText
          if (token.getElementType == HoconTokenType.HashComment) {
            val offset = CharArrayUtil.shiftForward(text, caretOffset, " \t")
            if (offset < document.getTextLength && text.charAt(offset) != '\n') {
              var prefix = "#"
              if (!StringUtil.startsWith(text, offset, prefix)) {
                if (text.charAt(caretOffset) != ' ' && !prefix.endsWith(" ")) {
                  prefix += " "
                }
                document.insertString(caretOffset, prefix)
              } else {
                val afterPrefix = offset + prefix.length
                if (afterPrefix < document.getTextLength && text.charAt(afterPrefix) != ' ') {
                  document.insertString(afterPrefix, " ")
                }
                caretOffsetRef.set(offset)
              }
              Result.Default
            } else Result.Continue
          } else Result.Continue
        } else Result.Continue
      case _ => Result.Continue
    }

  override def postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result =
    file match {
      case _: HoconPsiFile =>
        val document = editor.getDocument
        PsiDocumentManager.getInstance(file.getProject).commitDocument(document)

        val caretModel = editor.getCaretModel
        val caretOffset = caretModel.getOffset
        val psiAtOffset = file.findElementAt(caretOffset)
        if (psiAtOffset == null) return Result.Continue

        lazy val prevPsi = notWhiteSpaceSibling(psiAtOffset)(_.getPrevSibling)

        def lineNumber(psi: PsiElement) =
          editor.getDocument.getLineNumber(psi.getTextRange.getStartOffset)

        def isHashComment(psi: PsiElement) =
          psi != null && psi.getNode.getElementType == HoconTokenType.HashComment

        if (isHashComment(psiAtOffset) && isHashComment(prevPsi) &&
          lineNumber(psiAtOffset) == lineNumber(prevPsi) + 1 &&
          caretOffset == psiAtOffset.getTextRange.getStartOffset) {

          caretModel.moveToOffset(caretOffset + 2)
          Result.Default
        } else Result.Continue
      case _ =>
        Result.Continue
    }
}
