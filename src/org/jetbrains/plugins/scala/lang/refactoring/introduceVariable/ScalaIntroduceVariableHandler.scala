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
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.{HelpID, RefactoringActionHandler}
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
    val offset = editor.getCaretModel.getOffset
    def hasSelection = editor.getSelectionModel.hasSelection
    def selectionStart = editor.getSelectionModel.getSelectionStart
    def selectionEnd = editor.getSelectionModel.getSelectionEnd

    val selectedElement: Option[PsiElement] = {
      val typeElem = ScalaRefactoringUtil.getTypeElement(project, editor, file, selectionStart, selectionEnd)
      val expr = ScalaRefactoringUtil.getExpression(project, editor, file, selectionStart, selectionEnd).map(_._1)
      typeElem.orElse(expr)
    }

    def getTypeElementAtOffset = {
      def findTypeElement(offset: Int) =
        Option(PsiTreeUtil.findElementOfClassAtOffset(file, offset, classOf[ScTypeElement], false))

      file.findElementAt(offset) match {
        case w: PsiWhiteSpace if w.getTextRange.getStartOffset == offset => findTypeElement(offset - 1)
        case _ => findTypeElement(offset)
      }
    }

    if (hasSelection && selectedElement.isEmpty) {
      val message = ScalaBundle.message("cannot.refactor.not.expression.nor.type")
      CommonRefactoringUtil.showErrorHint(project, editor, message, INTRODUCE_VARIABLE_REFACTORING_NAME, HelpID.INTRODUCE_VARIABLE)
      return
    }

    //clear data on startRefactoring, if there is no marks, but there is some data
    if (StartMarkAction.canStart(project) == null) {
      editor.putUserData(IntroduceTypeAlias.REVERT_TYPE_ALIAS_INFO, new IntroduceTypeAliasData())
    }

    val typeElement = selectedElement.collect { case te: ScTypeElement => te }
      .orElse(getTypeElementAtOffset)

    if (typeElement.isDefined) {
      if (editor.getUserData(IntroduceTypeAlias.REVERT_TYPE_ALIAS_INFO).isData) {
        invokeTypeElement(project, editor, file, typeElement.get)
      } else {
        ScalaRefactoringUtil.afterTypeElementChoosing(project, editor, file, dataContext, typeElement.get, INTRODUCE_TYPEALIAS_REFACTORING_NAME) {
          typeElement =>
            ScalaRefactoringUtil.trimSpacesAndComments(editor, file)
            invokeTypeElement(project, editor, file, typeElement)
        }
      }
    } else {
      val canBeIntroduced: ScExpression => Boolean = ScalaRefactoringUtil.checkCanBeIntroduced(_)
      ScalaRefactoringUtil.afterExpressionChoosing(project, editor, file, dataContext, INTRODUCE_VARIABLE_REFACTORING_NAME, canBeIntroduced) {
        ScalaRefactoringUtil.trimSpacesAndComments(editor, file)
        invokeExpression(project, editor, file, selectionStart, selectionEnd)
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