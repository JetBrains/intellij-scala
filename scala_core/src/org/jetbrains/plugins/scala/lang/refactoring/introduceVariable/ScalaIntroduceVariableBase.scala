package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable

import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import com.intellij.psi.PsiType
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
    val typez: PsiType = null
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

    //todo: find occurrences
    val occurrences: Array[PsiElement] = Array[PsiElement](expr)
    // Getting settings
    var validator: ScalaValidator = new ScalaVariableValidator(this, project, expr, occurrences, enclosingContainer)
    var dialog: ScalaIntroduceVariableDialogInterface = getDialog(project, editor, expr, typez, occurrences, false, validator)

    if (!dialog.isOK()) {
      return
    }

    var settings: ScalaIntroduceVariableSettings = dialog.getSettings();

    val varName: String = settings.getEnteredName()
    var varType: PsiType = settings.getSelectedType()
    val isVariable: Boolean = settings.isDeclareVariable()
    val replaceAllOccurrences: Boolean = settings.isReplaceAllOccurrences()

    // Generating varibable declaration
    val varDecl: PsiElement = ScalaPsiElementFactory.createDeclaration(varType, varName,
                     isVariable, expr /*do unpar*/ , file.getManager)

    //runRefactoring(selectedExpr, editor, tempContainer, occurrences, varName, varType, replaceAllOccurrences, varDecl);

    return

  }

  def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext) {
    //nothing to do
  }

  protected def showErrorMessage(text: String, project: Project)

  protected def getDialog(project: Project, editor: Editor, expr: ScExpression, typez: PsiType, occurrences: Array[PsiElement],
                         declareVariable: Boolean, validator: ScalaValidator): ScalaIntroduceVariableDialogInterface
}