package org.jetbrains.plugins.scala
package editor
package enterHandler

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.lang.ASTNode
import com.intellij.lexer.StringLiteralLexer
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.{PsiElement, PsiFile, StringEscapesTokenTypes}
import org.jetbrains.plugins.scala.lang.TokenSets
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral

/**
 * User: Dmitry Naydanov
 * Date: 3/31/12
 */

class InterpolatedStringEnterHandler extends EnterHandlerDelegateAdapter {
  override def preprocessEnter(file: PsiFile, editor: Editor, caretOffset: Ref[Integer], caretAdvance: Ref[Integer],
                               dataContext: DataContext, originalHandler: EditorActionHandler): Result = {

    var offset = caretOffset.get().intValue()

    if (!file.isInstanceOf[ScalaFile] || !editor.inScalaString(offset)) return Result.Continue

    editor.commitDocument(file.getProject)  // TODO: AVOID COMMITTING DOCUMENTS ON TYPING!

    val element = file.findElementAt(offset)

    import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._

    def modifyOffset(moveOn: Int) {
      offset += moveOn
      caretOffset.set(caretOffset.get + moveOn)
    }

    def isMLString(element: PsiElement) = element match {
      case lit: ScLiteral => lit.isMultiLineString
      case _ => false
    }

    Option(element) foreach (a =>
      if (Set(tINTERPOLATED_STRING, tINTERPOLATED_STRING_ESCAPE, tINTERPOLATED_STRING_END,
        tINTERPOLATED_STRING_INJECTION).contains(a.getNode.getElementType)) {
        a.getParent.getFirstChild.getNode match {
          case b: ASTNode if TokenSets.INTERPOLATED_PREFIX_TOKEN_SET.contains(b.getElementType) =>
            if (a.getNode.getElementType == tINTERPOLATED_STRING_ESCAPE) {
              if (caretOffset.get - a.getTextOffset == 1) modifyOffset(1)
            } else {
              val lexer = new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, a.getNode.getElementType)
              lexer.start(a.getText, 0, a.getTextLength)

              do {
                if (lexer.getTokenStart + a.getTextOffset < caretOffset.get && caretOffset.get() < lexer.getTokenEnd + a.getTextOffset) {
                  if (StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(lexer.getTokenType)) {
                    modifyOffset(lexer.getTokenEnd + a.getTextOffset - caretOffset.get())
                  }
                }
              } while (caretOffset.get() > lexer.getTokenEnd + a.getTextOffset && (lexer.advance(), lexer.getTokenType != null)._2)
            }


            extensions.inWriteAction {
              if (isMLString(a.getParent)) return Result.Continue

              caretOffset.set(caretOffset.get + 3)
              caretAdvance.set(b.getTextLength + 1)
              editor.getDocument.insertString(offset, "\" +" + b.getText + "\"")
            }
            return Result.Continue
          case _ =>
        }
      })

    Result.Continue
  }
}
