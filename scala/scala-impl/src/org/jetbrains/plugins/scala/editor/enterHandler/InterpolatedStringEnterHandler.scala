package org.jetbrains.plugins.scala.editor.enterHandler

import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.lang.ASTNode
import com.intellij.lexer.StringLiteralLexer
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.util.Ref
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.{PsiElement, PsiFile, StringEscapesTokenTypes}
import org.jetbrains.plugins.scala.editor.EditorExt
import org.jetbrains.plugins.scala.editor.enterHandler.InterpolatedStringEnterHandler.InterpolatedTokenSet
import org.jetbrains.plugins.scala.extensions.{ObjectExt, inWriteAction}
import org.jetbrains.plugins.scala.lang.TokenSets
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes.{tINTERPOLATED_STRING, tINTERPOLATED_STRING_END, tINTERPOLATED_STRING_ESCAPE, tINTERPOLATED_STRING_INJECTION}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral

/**
 * The action is responsible for splitting interpolated string.
 * It handles only single line strings with 1 exception when the caret is inside the `$$` escape.
 *
 * Example 1: {{{
 *   Before : s"hello CARET world"
 *   After  : s"hello "\n + s"CARET world"
 * }}}
 *
 * Example 2: {{{
 *   Before : s"hello $CARET$ world"
 *   After  : s"hello $$"\n + s"CARET world"
 * }}}
 */
class InterpolatedStringEnterHandler extends EnterHandlerDelegateAdapter {

  override def preprocessEnter(
    file: PsiFile,
    editor: Editor,
    caretOffset: Ref[Integer],
    caretAdvance: Ref[Integer],
    dataContext: DataContext,
    originalHandler: EditorActionHandler
  ): Result = {
    val caretOffsetInitValue = caretOffset.get().intValue()

    if (!file.is[ScalaFile] || !editor.inScalaString(caretOffsetInitValue))
      return Result.Continue

    //ATTENTION: don't commit the document in any editor typing actions - it's an expensive operation that can take another 30ms on a powerful machine
    //editor.commitDocument(project)

    val element = file.findElementAt(caretOffsetInitValue)
    if (element == null)
      return Result.Continue

    val isInterpolatedString = InterpolatedTokenSet.contains(element.getNode.getElementType)
    if (!isInterpolatedString)
      return Result.Continue

    val parentFirstChildNode = element.getParent.getFirstChild.getNode
    val hasInterpolator = parentFirstChildNode match {
      case b: ASTNode if TokenSets.INTERPOLATED_PREFIX_TOKEN_SET.contains(b.getElementType) => true
      case _ => false
    }
    // NOTE: not sure why this extra check is needed (interpolated string should always have interpolator)
    // but that's what we had before, so I will just leave it here...
    if (!hasInterpolator)
      return Result.Continue
    val interpolator = parentFirstChildNode

    //NOTE: not clear why we need this extra var if we already have modifiable caretOffset
    var caretOffsetNewValue = caretOffsetInitValue
    def modifyOffset(moveOn: Int): Unit = {
      caretOffsetNewValue += moveOn
      caretOffset.set(caretOffset.get + moveOn)
    }

    val isInside$$Escape = element.getNode.getElementType == tINTERPOLATED_STRING_ESCAPE && caretOffset.get - element.getTextOffset == 1
    if (isInside$$Escape) {
      // If the caret is inside `s"$$"` (s"$caret$"), just shift the caret 1 character, not to break the escaping of $
      modifyOffset(1)
    } else if (isMultilineString(element.getParent)) {
      // Do nothing more for multiline strings
    } else {
      val lexer = new StringLiteralLexer(StringLiteralLexer.NO_QUOTE_CHAR, element.getNode.getElementType)
      lexer.start(element.getText, 0, element.getTextLength)

      do {
        if (lexer.getTokenStart + element.getTextOffset < caretOffset.get && caretOffset.get() < lexer.getTokenEnd + element.getTextOffset) {
          if (StringEscapesTokenTypes.STRING_LITERAL_ESCAPES.contains(lexer.getTokenType)) {
            modifyOffset(lexer.getTokenEnd + element.getTextOffset - caretOffset.get())
          }
        }
      } while (caretOffset.get() > lexer.getTokenEnd + element.getTextOffset && (lexer.advance(), lexer.getTokenType != null)._2)

      inWriteAction {
        // Complete the current string literal with the closing quote and with the ` + ` symbol
        val closingQuoteWithStringConcat = "\" +"
        val stringSplitText = closingQuoteWithStringConcat + interpolator.getText + "\""
        editor.getDocument.insertString(caretOffsetNewValue, stringSplitText)

        // Shift the caret before inserting the actual new line for the Enter action (otherwise we will split the newly-added `ClosingQuoteWithStringConcat`)
        caretOffset.set(caretOffset.get + closingQuoteWithStringConcat.length)

        // After the enter action is done, put the caret in the new string just after the quote
        val newStringContentOffset = interpolator.getTextLength + 1
        caretAdvance.set(newStringContentOffset)
      }
    }

    Result.Continue
  }

  private def isMultilineString(element: PsiElement) = element match {
    case lit: ScStringLiteral => lit.isMultiLineString
    case _ => false
  }
}

object InterpolatedStringEnterHandler {

  private val InterpolatedTokenSet = TokenSet.create(
    tINTERPOLATED_STRING,
    tINTERPOLATED_STRING_ESCAPE,
    tINTERPOLATED_STRING_END,
    tINTERPOLATED_STRING_INJECTION
  )
}
