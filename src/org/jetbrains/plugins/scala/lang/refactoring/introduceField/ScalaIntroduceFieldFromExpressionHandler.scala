package org.jetbrains.plugins.scala
package lang.refactoring.introduceField

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiFile, PsiElement}
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.refactoring.util.{ConflictsReporter, ScalaRefactoringUtil, ScalaVariableValidator}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import ScalaRefactoringUtil._
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester
import com.intellij.openapi.wm.WindowManager
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.ScalaUtils
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil


/**
 * Nikolay.Tropin
 * 6/27/13
 */
class ScalaIntroduceFieldFromExpressionHandler extends ScalaIntroduceFieldHandlerBase {
  def invoke(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int) {
    try {
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      ScalaRefactoringUtil.checkFile(file, project, editor, REFACTORING_NAME)

      val (expr: ScExpression, scType: ScType) = getExpression(project, editor, file, startOffset, endOffset).
              getOrElse(showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression"), project, editor, REFACTORING_NAME))
      val types = ScalaRefactoringUtil.addPossibleTypes(scType, expr)
      afterClassChoosing[ScExpression](expr, types, project, editor, file, "Choose class for Introduce Field") {
        convertExpressionToField
      }
    }
    catch {
      case _: IntroduceException => return
    }
  }

  override def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    def invokes() {
      ScalaRefactoringUtil.trimSpacesAndComments(editor, file)
      invoke(project, editor, file, editor.getSelectionModel.getSelectionStart, editor.getSelectionModel.getSelectionEnd)
    }
    val canBeIntroduced: (ScExpression) => Boolean = ScalaRefactoringUtil.checkCanBeIntroduced(_)
    ScalaRefactoringUtil.invokeRefactoring(project, editor, file, dataContext, REFACTORING_NAME, invokes, canBeIntroduced)
  }

  override def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext) {
    //nothing
  }

  def convertExpressionToField(expr: ScExpression, types: Array[ScType], aClass: ScTemplateDefinition, project: Project, editor: Editor, file: PsiFile) {
    ScalaRefactoringUtil.checkCanBeIntroduced(expr, showErrorMessage(_, project, editor, REFACTORING_NAME))

    val fileEncloser = ScalaRefactoringUtil.fileEncloser(expr.getTextOffset, file)
    val occurrencesAll: Array[TextRange] = ScalaRefactoringUtil.getOccurrenceRanges(ScalaRefactoringUtil.unparExpr(expr), fileEncloser)
    val occurrences = occurrencesAll.filterNot(ScalaRefactoringUtil.isLiteralPattern(file, _))
    val validator = ScalaVariableValidator(new ConflictsReporter {
      def reportConflicts(conflicts: Array[String], project: Project): Boolean = false
    }, project, editor, file, expr, occurrences)

    def runWithDialog() {
      val dialog = getDialog(project, editor, expr, types, occurrences, declareVariable = false, validator)
      if (!dialog.isOK) return
      val settings = new Settings(dialog.getEnteredName, dialog.getSelectedType)
      runRefactoring(expr, types, aClass, occurrences, editor, settings)
    }

    runWithDialog()

  }

  private def runRefactoringInside(expr: ScExpression, types: Array[ScType], aClass: ScTemplateDefinition,
                                   occurrences: Array[TextRange], editor: Editor, settings: Settings) {
    def findAnchor(expr: ScExpression, occurrences: Array[PsiElement], aClass: ScTemplateDefinition): PsiElement = {
      val body = aClass.extendsBlock.templateBody
      val stmts: Seq[PsiElement] = body.toSeq.flatMap(_.children)
      val firstOccOffset = occurrences.map(_.getTextRange.getStartOffset).min
      stmts.find(_.getTextRange.getEndOffset > firstOccOffset).get
    }

    val expression = ScalaRefactoringUtil.expressionToIntroduce(expr)
    val mainOcc = occurrences.filter(_.getStartOffset == editor.getSelectionModel.getSelectionStart)
    val occurrencesToReplace = if (settings.replaceAll) occurrences else mainOcc
    val name = settings.name
    val replacedOccurences = ScalaRefactoringUtil.replaceOccurences(occurrencesToReplace, name, aClass.getContainingFile, editor)
            .map(_.asInstanceOf[PsiElement])
    val anchor: PsiElement = findAnchor(expression, replacedOccurences, aClass)
    val createdDeclaration = ScalaPsiElementFactory
            .createDeclaration(types(0), name, settings.isVar, expression, aClass.getManager, isPresentableText = false)
    import ScalaApplicationSettings.AccessLevel
    val accessModifier = settings.accessLevel match {
      case AccessLevel.DEFAULT => None
      case AccessLevel.PRIVATE => Some(ScalaPsiElementFactory.createModifierFromText("private", createdDeclaration.getManager))
      case AccessLevel.PROTECTED => Some(ScalaPsiElementFactory.createModifierFromText("protected", createdDeclaration.getManager))
    }
    accessModifier.foreach {
      mod =>
        val whitespace: PsiElement = ScalaPsiElementFactory.createWhitespace(createdDeclaration.getManager)
        createdDeclaration.addBefore(whitespace, createdDeclaration.getFirstChild)
        createdDeclaration.addBefore(mod.getPsi, createdDeclaration.getFirstChild)
    }
    val parent = anchor.getParent
    val declaration = parent.addBefore(createdDeclaration, anchor)
    parent.addBefore(ScalaPsiElementFactory.createNewLineNode(anchor.getManager, "\n").getPsi, anchor)
    ScalaPsiUtil.adjustTypes(declaration)
  }

  def runRefactoring(expr: ScExpression, types: Array[ScType], aClass: ScTemplateDefinition, occurrences: Array[TextRange], editor: Editor, settings: Settings) {
    val runnable = new Runnable {
      def run() = runRefactoringInside(expr, types, aClass, occurrences, editor, settings)
    }
    ScalaUtils.runWriteAction(runnable, editor.getProject, REFACTORING_NAME)
    editor.getSelectionModel.removeSelection()
  }

  protected def getDialog(project: Project, editor: Editor, expr: ScExpression, typez: Array[ScType],
                          occurrences: Array[TextRange], declareVariable: Boolean,
                          validator: ScalaVariableValidator): ScalaIntroduceFieldDialog = {
    // Add occurrences highlighting
    if (occurrences.length > 1)
      ScalaRefactoringUtil.highlightOccurrences(project, occurrences, editor)

    val possibleNames = NameSuggester.suggestNames(expr, validator)
    val dialog = new ScalaIntroduceFieldDialog(project, typez, occurrences.length, validator, possibleNames)
    dialog.show()
    if (!dialog.isOK) {
      if (occurrences.length > 1) {
        WindowManager.getInstance.getStatusBar(project).
                setInfo(ScalaBundle.message("press.escape.to.remove.the.highlighting"))
      }
    }
    dialog
  }

}
