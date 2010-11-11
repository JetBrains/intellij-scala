package org.jetbrains.plugins.scala.editor.typedHandler

import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate.Result
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.{PsiDocumentManager, PsiWhiteSpace, PsiFile}

/**
 * @author Alexander Podkhalyuzin
 */

class ScalaTypedHandler extends TypedHandlerDelegate {
  override def charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result = {
    if (!file.isInstanceOf[ScalaFile]) return Result.CONTINUE
    if (c == ' ') {
      val action: Runnable = new Runnable {
        def run: Unit = {
          PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
        }
      }
      ApplicationManager.getApplication.runWriteAction(action)
      val offset = editor.getCaretModel.getOffset
      val element = file.findElementAt(offset - 1)
      if (element.isInstanceOf[PsiWhiteSpace] || ScalaPsiUtil.isLineTerminator(element)) {
        val anotherElement = file.findElementAt(offset - 2)
        if (anotherElement.getNode.getElementType == ScalaTokenTypes.kCASE &&
                anotherElement.getParent.isInstanceOf[ScCaseClause]) {
          val action: Runnable = new Runnable {
            def run: Unit = {
              PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
              CodeStyleManager.getInstance(project).adjustLineIndent(file, anotherElement.getTextRange)
            }
          }
          ApplicationManager.getApplication.runWriteAction(action)
          return Result.STOP
        }
      }
    }
    Result.CONTINUE
  }
}