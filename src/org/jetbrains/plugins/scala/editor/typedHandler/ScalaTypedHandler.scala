package org.jetbrains.plugins.scala.editor.typedHandler

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import com.intellij.psi.{PsiWhiteSpace, PsiFile}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import com.intellij.psi.codeStyle.CodeStyleManager

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaTypedHandler extends TypedHandlerDelegate {
  override def charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result = {
    if (!file.isInstanceOf[ScalaFile]) return Result.CONTINUE
    if (c == ' ') {
      val offset = editor.getCaretModel.getOffset
      val element = file.findElementAt(offset - 1)
      if (element.isInstanceOf[PsiWhiteSpace] || ScalaPsiUtil.isLineTerminator(element)) {
        val anotherElement = file.findElementAt(offset - 2)
        if (anotherElement.getNode.getElementType == ScalaTokenTypes.kCASE &&
                anotherElement.getParent.isInstanceOf[ScCaseClause]) {
          CodeStyleManager.getInstance(project).adjustLineIndent(file, anotherElement.getTextRange)
          return Result.STOP
        }
      }
    }
    Result.CONTINUE
  }
}