package org.jetbrains.plugins.scala
package lang
package refactoring
package introduceVariable

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.impl.StartMarkAction
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.{Editor, SelectionModel}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util._
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil.findElementOfClassAtOffset
import com.intellij.refactoring.HelpID
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.IntroduceTypeAlias.REVERT_TYPE_ALIAS_INFO
import org.jetbrains.plugins.scala.lang.refactoring.util.DialogConflictsReporter
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil._

/**
  * User: Alexander Podkhalyuzin
  * Date: 23.06.2008
  */
class ScalaIntroduceVariableHandler extends ScalaRefactoringActionHandler with DialogConflictsReporter with IntroduceExpressions with IntroduceTypeAlias {

  private var occurrenceHighlighters: collection.Seq[RangeHighlighter] = Seq.empty

  override def invoke(file: PsiFile)
                     (implicit project: Project, editor: Editor, dataContext: DataContext): Unit = {
    val scalaFile = file.findScalaLikeFile.orNull
    if (scalaFile == null) return

    trimSpacesAndComments(editor, scalaFile)

    implicit val selectionModel: SelectionModel = editor.getSelectionModel
    val maybeSelectedElement = getTypeElement(scalaFile).orElse(getExpression(scalaFile))

    def getTypeElementAtOffset: Option[ScTypeElement] = {
      val offset = editor.getCaretModel.getOffset
      val diff = scalaFile.findElementAt(offset) match {
        case w: PsiWhiteSpace if w.getTextRange.getStartOffset == offset => 1
        case _ => 0
      }

      val realOffset = offset - diff
      if (possibleExpressionsToExtract(scalaFile, realOffset).isEmpty)
        Option(findElementOfClassAtOffset(scalaFile, realOffset, classOf[ScTypeElement], false))
      else None
    }

    if (selectionModel.hasSelection && maybeSelectedElement.isEmpty) {
      showErrorHint(ScalaBundle.message("cannot.refactor.not.expression.nor.type"), INTRODUCE_VARIABLE_REFACTORING_NAME, HelpID.INTRODUCE_VARIABLE)
      return
    }

    //clear data on startRefactoring, if there is no marks, but there is some data
    if (StartMarkAction.canStart(project) == null) {
      editor.putUserData(REVERT_TYPE_ALIAS_INFO, new IntroduceTypeAliasData())
    }

    val maybeTypeElement = maybeSelectedElement match {
      case Some(typeElement: ScTypeElement) => Some(typeElement)
      case _ if !selectionModel.hasSelection => getTypeElementAtOffset
      case _ => None
    }

    maybeTypeElement match {
      case Some(typeElement) if editor.getUserData(REVERT_TYPE_ALIAS_INFO).isData =>
        invokeTypeElement(scalaFile, typeElement)
      case Some(typeElement) =>
        afterTypeElementChoosing(typeElement, INTRODUCE_TYPEALIAS_REFACTORING_NAME) {
          invokeTypeElement(scalaFile, _)
        }
      case _ =>
        afterExpressionChoosing(scalaFile, INTRODUCE_VARIABLE_REFACTORING_NAME) {
          invokeExpression(scalaFile, selectionModel.getSelectionStart, selectionModel.getSelectionEnd)
        }
    }
  }

  protected def showDialogImpl[D <: DialogWrapper](dialog: D,
                                                   occurrences: Seq[TextRange])
                                                  (implicit project: Project, editor: Editor): Option[D] = {
    val multipleOccurrences = occurrences.length > 1
    if (multipleOccurrences) {
      occurrenceHighlighters = highlightOccurrences(project, occurrences, editor)
    }

    dialog.show()
    if (dialog.isOK) Some(dialog) else {
      if (multipleOccurrences) {
        WindowManager.getInstance
          .getStatusBar(project)
          .setInfo(ScalaBundle.message("press.escape.to.remove.the.highlighting"))
      }

      occurrenceHighlighters.foreach(_.dispose())
      occurrenceHighlighters = Seq.empty

      None
    }
  }
}

object ScalaIntroduceVariableHandler {
  val REVERT_INFO: Key[RevertInfo] = new Key("RevertInfo")
}