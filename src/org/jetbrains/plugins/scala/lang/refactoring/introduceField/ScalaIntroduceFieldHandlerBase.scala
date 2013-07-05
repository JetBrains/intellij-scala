package org.jetbrains.plugins.scala
package lang.refactoring.introduceField

import com.intellij.refactoring.{HelpID, RefactoringActionHandler}
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScNewTemplateDefinition, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import scala.collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.PsiClassListCellRenderer
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.refactoring.introduceField.{ElementToWorkOn, BaseExpressionToFieldHandler}
import com.intellij.refactoring.introduceField.BaseExpressionToFieldHandler.Settings
import com.intellij.refactoring.util.occurrences.OccurrenceManager
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer

/**
 * Nikolay.Tropin
 * 6/28/13
 */
abstract class ScalaIntroduceFieldHandlerBase extends BaseExpressionToFieldHandler(false) {

  val REFACTORING_NAME = ScalaBundle.message("introduce.field.title")

  def convertExpressionToField(expr: ScExpression, aClass: ScTemplateDefinition, project: Project, editor: Editor, file: PsiFile) {

  }
  def invokeImpl(project: Project, localVariable: PsiLocalVariable, editor: Editor): Boolean = ???

  def createOccurrenceManager(selectedExpr: PsiExpression, parentClass: PsiClass): OccurrenceManager = ???

  def validClass(parentClass: PsiClass, editor: Editor): Boolean = parentClass.isInstanceOf[ScTemplateDefinition]

  def getInplaceIntroducer: AbstractInplaceIntroducer[_ <: PsiNameIdentifierOwner, _ <: PsiElement] = null

  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    def invokes() {
      ScalaRefactoringUtil.trimSpacesAndComments(editor, file)
      invoke(project, editor, file, editor.getSelectionModel.getSelectionStart, editor.getSelectionModel.getSelectionEnd)
    }
    val canBeIntroduced: (ScExpression) => Boolean = ScalaRefactoringUtil.checkCanBeIntroduced(_)
    ScalaRefactoringUtil.invokeRefactoring(project, editor, file, dataContext, "Introduce Field", invokes, canBeIntroduced)
  }

  def invoke(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int) {
    try {
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      ScalaRefactoringUtil.checkFile(file, project, editor, REFACTORING_NAME)

      val (expr: ScExpression, scType: ScType) = ScalaRefactoringUtil.getExpression(project, editor, file, startOffset, endOffset).
              getOrElse(showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression"), project, editor, REFACTORING_NAME))
      val types = ScalaRefactoringUtil.addPossibleTypes(scType, expr)
      val classes = ScalaPsiUtil.getParents(expr, file).collect{ case t: ScTemplateDefinition => t}.toArray[PsiClass]
      classes.size match {
        case 0 =>
        case 1 => convertExpressionToField(expr, classes(0).asInstanceOf[ScTemplateDefinition], project, editor, file)
        case _ =>
          val selection = classes(0)
          val processor = new PsiElementProcessor[PsiClass] {
            def execute(aClass: PsiClass): Boolean = {
              convertExpressionToField(expr, aClass.asInstanceOf[ScTemplateDefinition], project, editor, file)
              false
            }
          }
          NavigationUtil.getPsiElementPopup(classes, new PsiClassListCellRenderer(),
            "Choose class to introduce field", processor, selection).showInBestPositionFor(editor)
      }


    }
    catch {
      case _: IntroduceException => return
    }
  }

  def showRefactoringDialog(project: Project, editor: Editor, parentClass: PsiClass, expr: PsiExpression, `type`: PsiType, occurrences: Array[PsiExpression], anchorElement: PsiElement, anchorElementIfAll: PsiElement): Settings = ???

  def accept(elementToWorkOn: ElementToWorkOn): Boolean = ???

}
