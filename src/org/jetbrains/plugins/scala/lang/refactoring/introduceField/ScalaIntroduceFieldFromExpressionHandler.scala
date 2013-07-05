package org.jetbrains.plugins.scala
package lang.refactoring.introduceField

import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiClass, PsiDocumentManager, PsiFile, PsiElement}
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import scala.collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil.{IntroduceException, showErrorMessage}
import com.intellij.refactoring.introduceField.IntroduceFieldHandler
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.ide.util.PsiClassListCellRenderer


/**
 * Nikolay.Tropin
 * 6/27/13
 */
class ScalaIntroduceFieldFromExpressionHandler extends ScalaIntroduceFieldHandlerBase {
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

  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    def invokes() {
      ScalaRefactoringUtil.trimSpacesAndComments(editor, file)
      invoke(project, editor, file, editor.getSelectionModel.getSelectionStart, editor.getSelectionModel.getSelectionEnd)
    }
    val canBeIntroduced: (ScExpression) => Boolean = ScalaRefactoringUtil.checkCanBeIntroduced(_)
    ScalaRefactoringUtil.invokeRefactoring(project, editor, file, dataContext, REFACTORING_NAME, invokes, canBeIntroduced)
  }

  def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext) {
    //nothing
  }

  def convertExpressionToField(expr: ScExpression, aClass: ScTemplateDefinition, project: Project, editor: Editor, file: PsiFile) {

  }
}
