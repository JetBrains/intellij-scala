package org.jetbrains.plugins.scala.lang.refactoring

import com.intellij.openapi.vfs.ReadonlyStatusHandler
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScTryBlock
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock
import com.intellij.psi.PsiElement
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.PsiFile
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.lexer._

/**
* User: Alexander Podkhalyuzin
* Date: 23.06.2008
*/

object ScalaRefactoringUtil {
  def trimSpacesAndComments(editor: Editor, file: PsiFile) {
    var start = editor.getSelectionModel.getSelectionStart
    var end = editor.getSelectionModel.getSelectionEnd
    while (file.findElementAt(start).isInstanceOf[PsiWhiteSpace] ||
            file.findElementAt(start).isInstanceOf[PsiComment]) start = start + 1
    while (file.findElementAt(end - 1).isInstanceOf[PsiWhiteSpace] ||
            file.findElementAt(end - 1).isInstanceOf[PsiComment]) end = end - 1
    editor.getSelectionModel.setSelection(start, end)
  }

  def getExpression(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int): Option[ScExpression] = {
    val element1 = file.findElementAt(startOffset)
    val element2 = file.findElementAt(endOffset)
    if (element1 == null || element2 == null || element1 != element2 || !element1.isInstanceOf[ScExpression]) {
      return None
    }
    return Some(element1.asInstanceOf[ScExpression])
  }

  def getEnclosingContainer(expr: ScExpression): PsiElement = {
    var parent: PsiElement = expr
    while (parent != null &&
            !parent.isInstanceOf[ScBlockExpr] &&
            !parent.isInstanceOf[ScTryBlock] &&
            !parent.isInstanceOf[ScTemplateBody] &&
            !parent.isInstanceOf[ScBlock]) parent = parent.getParent
    return parent
  }
  def ensureFileWritable(project: Project, file: PsiFile): Boolean = {
    val virtualFile = file.getVirtualFile()
    val readonlyStatusHandler = ReadonlyStatusHandler.getInstance(project)
    val operationStatus = readonlyStatusHandler.ensureFilesWritable(Array(virtualFile))
    return !operationStatus.hasReadonlyFiles()
  }
}