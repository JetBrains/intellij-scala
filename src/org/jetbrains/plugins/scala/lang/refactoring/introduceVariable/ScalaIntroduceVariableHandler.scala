package org.jetbrains.plugins.scala
package lang
package refactoring
package introduceVariable

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{xml => _, _}
import com.intellij.refactoring.RefactoringActionHandler
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.refactoring.util.{DialogConflictsReporter, ScalaRefactoringUtil}


/**
 * User: Alexander Podkhalyuzin
 * Date: 23.06.2008
 */

class ScalaIntroduceVariableHandler extends RefactoringActionHandler with DialogConflictsReporter with IntroduceExpressions with IntroduceTypeAlias {
  var occurrenceHighlighters = Seq.empty[RangeHighlighter]

  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    def getElement(offset: Int) = PsiTreeUtil.findElementOfClassAtOffset(file, offset, classOf[ScTypeElement], false)
    val offset = editor.getCaretModel.getOffset

    def getRealElement = {
      if (editor.getSelectionModel.hasSelection) {
        ScalaRefactoringUtil.getTypeEement(project, editor, file, editor.getSelectionModel.getSelectionStart,
          editor.getSelectionModel.getSelectionEnd)
      } else {
        file.findElementAt(offset) match {
          case w: PsiWhiteSpace if w.getTextRange.getStartOffset == offset &&
            w.getText.contains(" ") =>
            Option(getElement(offset - 1))
          case _ => Option(getElement(offset))
        }
      }
    }

    val element = getRealElement

    //clear data on startRefactoring, if there is no marks, but there is some data
    if ((StartMarkAction.canStart(project) == null) && IntroduceTypeAliasData.isData) {
      IntroduceTypeAliasData.clearData()
    }

    if (element.isDefined) {
      if (IntroduceTypeAliasData.isData) {
        invokeTypeElement(project, editor, file, element.get)
      } else {
        ScalaRefactoringUtil.afterTypeElementChoosing(project, editor, file, dataContext, element.get, INTRODUCE_TYPEALIAS_REFACTORING_NAME) {
          typeElement =>
            ScalaRefactoringUtil.trimSpacesAndComments(editor, file)
            invokeTypeElement(project, editor, file, typeElement)
        }
      }
    } else {
      val canBeIntroduced: ScExpression => Boolean = ScalaRefactoringUtil.checkCanBeIntroduced(_)
      ScalaRefactoringUtil.afterExpressionChoosing(project, editor, file, dataContext, INTRODUCE_VARIABLE_REFACTORING_NAME, canBeIntroduced) {
        ScalaRefactoringUtil.trimSpacesAndComments(editor, file)
        invokeExpression(project, editor, file, editor.getSelectionModel.getSelectionStart, editor.getSelectionModel.getSelectionEnd)
      }
    }
  }

  def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext) {
    //nothing to do
  }
}

object ScalaIntroduceVariableHandler {
  val REVERT_INFO: Key[ScalaRefactoringUtil.RevertInfo] = new Key("RevertInfo")
}