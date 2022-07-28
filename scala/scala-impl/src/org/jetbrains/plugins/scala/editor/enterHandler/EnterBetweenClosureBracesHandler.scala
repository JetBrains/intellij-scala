package org.jetbrains.plugins.scala.editor
package enterHandler

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.util.Ref
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiFile, PsiWhiteSpace}
import com.intellij.util.IncorrectOperationException
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScFunctionExpr}

/**
  * Mostly copy-pasted from [[com.intellij.codeInsight.editorActions.enter.EnterBetweenBracesHandler]]
  */
class EnterBetweenClosureBracesHandler extends EnterHandlerDelegateAdapter {
  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.editor.enterHandler.EnterBetweenClosureBracesHandler")

  override def preprocessEnter(file: PsiFile, editor: Editor, caretOffset: Ref[Integer], caretAdvance: Ref[Integer],
                               dataContext: DataContext, originalHandler: EditorActionHandler): Result = {
    if (!file.isInstanceOf[ScalaFile]) {
      return Result.Continue
    }
    val project = file.getProject

    val document: Document = editor.getDocument
    val text: CharSequence = document.getCharsSequence
    val offset: Int = caretOffset.get.intValue
    if (!CodeInsightSettings.getInstance.SMART_INDENT_ON_ENTER) return Result.Continue
    val nextCharOffset: Int = CharArrayUtil.shiftForward(text, offset, " \t")
    if (!isValidOffset(nextCharOffset, text) || text.charAt(nextCharOffset) != '}') return Result.Continue

    document.commit(project)

    val element = Option(file.findElementAt(offset))
    if (element.map(e => PsiTreeUtil.skipSiblingsBackward(e, classOf[PsiWhiteSpace])).exists{
      case fun: ScFunctionExpr =>
        fun.lastChild.filter(b => b.isInstanceOf[ScBlock] && b.getTextRange.isEmpty).map(_.getPrevSibling).
          filter(_ != null).map(_.getNode.getElementType).
          exists(t => t == ScalaTokenTypes.tFUNTYPE_ASCII || t == ScalaTokenTypes.tFUNTYPE)
      case _ => false
    } && element.map(_.getParent).exists(_.isInstanceOf[ScBlock])) {
      originalHandler.execute(editor, editor.getCaretModel.getCurrentCaret, dataContext)

      document.commit(project)
      try {
        CodeStyleManager.getInstance(project).adjustLineIndent(file, editor.getCaretModel.getOffset)
      }
      catch {
        case e: IncorrectOperationException => LOG.error(e)
      }
    }
    Result.Continue
  }

  protected def isValidOffset(offset: Int, text: CharSequence): Boolean = offset >= 0 && offset < text.length()
}
