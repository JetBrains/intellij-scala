package org.jetbrains.plugins.scala.editor.enterHandler

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate.Result
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.{PsiFile, PsiWhiteSpace}
import org.jetbrains.plugins.scala.caches.measure
import org.jetbrains.plugins.scala.extensions.{&, ElementType, ObjectExt, Parent, PsiElementExt}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody

/**
 * Adds a space before `{` in this case:
 * Before: {{{
 *   class MyClass{Caret}
 * }}}
 * After: {{{
 *   class MyClass {
 *     Caret
 *   }
 * }}}
 *
 * Works in combination with [[ScalaEnterAfterUnmatchedBraceHandler]]
 */
final class FormatEmptyTemplateBodyAfterEnterHandler extends EnterHandlerDelegateAdapter {

  override def postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): Result = measure("FormatEmptyTemplateBodyAfterEnterHandler.postProcessEnter") {
    if (!isApplicable(file))
      return Result.Continue

    //ATTENTION: don't commit the document in any editor typing actions - it's an expensive operation that can take another 30ms on a powerful machine
    //editor.commitDocument(project)

    /**
     * Check for `{`.
     * Note, if we press Enter between braces (`{Caret}`), the caret will be moved to the closing brace location.
     * It's done in [[org.jetbrains.plugins.scala.editor.enterHandler.ScalaEnterAfterUnmatchedBraceHandler]].
     */

    val element = file.findElementAt(editor.getCaretModel.getOffset)
    element match {
      case ElementType(ScalaTokenTypes.tRBRACE) & Parent(body: ScTemplateBody) if isEmptyBodyWithoutSpaceBeforeIt(body) =>
        val blockNode = body.getNode
        editor.getDocument.insertString(blockNode.getTextRange.getStartOffset, " ")
      case _ =>
    }

    Result.Continue
  }

  private def isEmptyBodyWithoutSpaceBeforeIt(body: ScTemplateBody): Boolean =
    body.isEmpty && body.prevLeaf.exists(!_.is[PsiWhiteSpace])

  private def isApplicable(file: PsiFile): Boolean =
    file.is[ScalaFile] && CodeInsightSettings.getInstance.SMART_INDENT_ON_ENTER
}
