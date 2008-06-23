package org.jetbrains.plugins.scala.refactoring.introduceVariable

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.ScalaFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiFile
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.refactoring.RefactoringActionHandler

/**
* User: Alexander Podkhalyuzin
* Date: 23.06.2008
*/

abstract class ScalaIntroduceVariableBase extends RefactoringActionHandler {
  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    if (!editor.getSelectionModel().hasSelection()) {
      editor.getSelectionModel().selectLineAtCaret();
    }
    ScalaRefactoringUtil.trimSpacesAndComments(editor, file);
    invoke(project, editor, file, editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());
  }

  def invoke(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int) {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    if (!file.isInstanceOf[ScalaFile]) {
      showErrorMessage(ScalaBundle.message("only.for.scala", Array[Object]()), project)
      return
    }
    val expr = ScalaRefactoringUtil.getExpression(project, editor, file, startOffset, endOffset) match {
      case Some(x) => x
      case None => {
        showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression", Array[Object]()), project)
        return
      }
    }
    //todo: think about type when type inference
    var parent: PsiElement = expr
    while (parent != null && !parent.isInstanceOf[ScalaFile] && !parent.isInstanceOf[ScParameters]) parent = parent.getParent
    if (parent.isInstanceOf[ScParameters]) {
      showErrorMessage(ScalaBundle.message("refactoring.is.not.supported.in.method.parameters", Array[Object]()), project)
      return
    }
    val enclosingContainer: PsiElement = ScalaRefactoringUtil.getEnclosingContainer(expr)
    if (enclosingContainer == null) {
      showErrorMessage(ScalaBundle.message("wrong.refactoring.context", Array[Object]()), project)
      return
    }
    
  }

  def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext) {
    //nothing to do
  }

  protected def showErrorMessage(text: String, project: Project)
}