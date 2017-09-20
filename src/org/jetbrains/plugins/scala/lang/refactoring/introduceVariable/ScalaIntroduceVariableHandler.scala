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
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil.findElementOfClassAtOffset
import com.intellij.refactoring.HelpID
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.IntroduceTypeAlias.REVERT_TYPE_ALIAS_INFO
import org.jetbrains.plugins.scala.lang.refactoring.util.DialogConflictsReporter
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil._

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.06.2008
 */
class ScalaIntroduceVariableHandler extends ScalaRefactoringActionHandler with DialogConflictsReporter with IntroduceExpressions with IntroduceTypeAlias {

  var occurrenceHighlighters = Seq.empty[RangeHighlighter]

  override def invoke(file: PsiFile)
                     (implicit project: Project, editor: Editor, dataContext: DataContext): Unit = {
    val offset = editor.getCaretModel.getOffset
    def hasSelection = editor.getSelectionModel.hasSelection
    def selectionStart = editor.getSelectionModel.getSelectionStart
    def selectionEnd = editor.getSelectionModel.getSelectionEnd

    trimSpacesAndComments(editor, file)

    val maybeSelectedElement = getTypeElement(project, editor, file, selectionStart, selectionEnd)
      .orElse(getExpression(project, editor, file, selectionStart, selectionEnd).map(_._1))


    def getTypeElementAtOffset = {
      val diff = file.findElementAt(offset) match {
        case w: PsiWhiteSpace if w.getTextRange.getStartOffset == offset => 1
        case _ => 0
      }

      val realOffset = offset - diff
      if (!hasSelection && getExpressionsAtOffset(file, realOffset).isEmpty)
        Option(findElementOfClassAtOffset(file, realOffset, classOf[ScTypeElement], false))
      else None
    }

    if (hasSelection && maybeSelectedElement.isEmpty) {
      val message = ScalaBundle.message("cannot.refactor.not.expression.nor.type")
      showErrorHint(message, INTRODUCE_VARIABLE_REFACTORING_NAME, HelpID.INTRODUCE_VARIABLE)
      return
    }

    //clear data on startRefactoring, if there is no marks, but there is some data
    if (StartMarkAction.canStart(project) == null) {
      editor.putUserData(REVERT_TYPE_ALIAS_INFO, new IntroduceTypeAliasData())
    }

    val maybeTypeElement = maybeSelectedElement match {
      case Some(te: ScTypeElement) => Option(te)
      case _ => getTypeElementAtOffset
    }

    maybeTypeElement match {
      case Some(typeElement) if editor.getUserData(REVERT_TYPE_ALIAS_INFO).isData =>
        invokeTypeElement(file, typeElement)
      case Some(typeElement) =>
        afterTypeElementChoosing(typeElement, INTRODUCE_TYPEALIAS_REFACTORING_NAME) {
          invokeTypeElement(file, _)
        }
      case _ =>
        afterExpressionChoosing(file, INTRODUCE_VARIABLE_REFACTORING_NAME) {
          invokeExpression(file, selectionStart, selectionEnd)
        }
    }
  }
}

object ScalaIntroduceVariableHandler {
  val REVERT_INFO: Key[RevertInfo] = new Key("RevertInfo")
}