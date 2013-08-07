package org.jetbrains.plugins.scala
package lang.refactoring.introduceField

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiFile, PsiElement}
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import ScalaRefactoringUtil._
import ScalaIntroduceFieldHandlerBase._
import com.intellij.openapi.wm.WindowManager
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.ScalaUtils
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import scala.Some


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

  def convertExpressionToField(ifc: IntroduceFieldContext[ScExpression]) {

    ScalaRefactoringUtil.checkCanBeIntroduced(ifc.element, showErrorMessage(_, ifc.project, ifc.editor, REFACTORING_NAME))

    def runWithDialog() {
      val settings = new IntroduceFieldSettings(ifc)
      if (!settings.canBeInitInDeclaration && !settings.canBeInitLocally) {
        ScalaRefactoringUtil.showErrorMessage("Can not create field from this expression", ifc.project, ifc.editor,
          ScalaBundle.message("introduce.field.title"))
      } else {
        val dialog = getDialog(ifc, settings)
        if (dialog.isOK) {
          runRefactoring(ifc, settings)
        }
      }
    }

    runWithDialog()

  }



  private def runRefactoringInside(ifc: IntroduceFieldContext[ScExpression], settings: IntroduceFieldSettings[ScExpression]) {
    val expression = ScalaRefactoringUtil.expressionToIntroduce(ifc.element)
    val mainOcc = ifc.occurrences.filter(_.getStartOffset == ifc.editor.getSelectionModel.getSelectionStart)
    val occurrencesToReplace = if (settings.replaceAll) ifc.occurrences else mainOcc
    val aClass = ifc.aClass
    val manager = aClass.getManager
    val name = settings.name
    val typeName = settings.scType.canonicalText
    val replacedOccurences = ScalaRefactoringUtil.replaceOccurences(occurrencesToReplace, name, ifc.file, ifc.editor)
            .map(_.asInstanceOf[PsiElement])
    val anchor: PsiElement = anchorForNewDeclaration(expression, replacedOccurences, aClass)
    val initInDecl = settings.initInDeclaration
    var createdDeclaration: PsiElement = null
    if (initInDecl) {
      createdDeclaration = ScalaPsiElementFactory
              .createDeclaration(name, typeName, settings.defineVar, expression, manager)
    } else {
      val underscore = ScalaPsiElementFactory.createExpressionFromText("_", manager)
      createdDeclaration = ScalaPsiElementFactory
              .createDeclaration(name, typeName, settings.defineVar, underscore, manager)

      anchorForInitializer(replacedOccurences.map(_.getTextRange), ifc.file) match {
        case Some(anchorForInit) =>
          val parent = anchorForInit.getParent
          val assignStmt = ScalaPsiElementFactory.createExpressionFromText(s"$name = ${expression.getText}", manager)
          parent.addBefore(assignStmt, anchorForInit)
          parent.addBefore(ScalaPsiElementFactory.createNewLineNode(manager, "\n").getPsi, anchorForInit)
        case None => throw new IntroduceException

      }
    }

    import ScalaApplicationSettings.VisibilityLevel
    val accessModifier = settings.visibilityLevel match {
      case VisibilityLevel.DEFAULT => None
      case VisibilityLevel.PRIVATE => Some(ScalaPsiElementFactory.createModifierFromText("private", createdDeclaration.getManager))
      case VisibilityLevel.PROTECTED => Some(ScalaPsiElementFactory.createModifierFromText("protected", createdDeclaration.getManager))
    }
    accessModifier.foreach {
      mod =>
        val whitespace: PsiElement = ScalaPsiElementFactory.createWhitespace(manager)
        createdDeclaration.addBefore(whitespace, createdDeclaration.getFirstChild)
        createdDeclaration.addBefore(mod.getPsi, createdDeclaration.getFirstChild)
    }
    val parent = anchor.getParent
    createdDeclaration = parent.addBefore(createdDeclaration, anchor)
    parent.addBefore(ScalaPsiElementFactory.createNewLineNode(manager, "\n").getPsi, anchor)
    ScalaPsiUtil.adjustTypes(createdDeclaration)
  }

  def runRefactoring(ifc: IntroduceFieldContext[ScExpression], settings: IntroduceFieldSettings[ScExpression]) {
    val runnable = new Runnable {
      def run() = runRefactoringInside(ifc, settings)
    }
    ScalaUtils.runWriteAction(runnable, ifc.project, REFACTORING_NAME)
    ifc.editor.getSelectionModel.removeSelection()
  }

  protected def getDialog(ifc: IntroduceFieldContext[ScExpression], settings: IntroduceFieldSettings[ScExpression]): ScalaIntroduceFieldDialog = {
    val occCount = ifc.occurrences.length
    // Add occurrences highlighting
    if (occCount > 1)
      ScalaRefactoringUtil.highlightOccurrences(ifc.project, ifc.occurrences, ifc.editor)

    val dialog = new ScalaIntroduceFieldDialog(ifc, settings)
    dialog.show()
    if (!dialog.isOK) {
      if (occCount > 1) {
        WindowManager.getInstance.getStatusBar(ifc.project).
                setInfo(ScalaBundle.message("press.escape.to.remove.the.highlighting"))
      }
    }
    dialog
  }

  protected override def isSuitableClass(elem: PsiElement, clazz: ScTemplateDefinition): Boolean = {
    elem != clazz
  }
}
