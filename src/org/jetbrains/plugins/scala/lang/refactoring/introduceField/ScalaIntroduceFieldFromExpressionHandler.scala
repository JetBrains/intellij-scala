package org.jetbrains.plugins.scala
package lang.refactoring.introduceField

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiFile, PsiElement}
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.{Document, Editor}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition}
import ScalaRefactoringUtil._
import ScalaIntroduceFieldHandlerBase._
import com.intellij.openapi.wm.WindowManager
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.util.ScalaUtils
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import scala.Some
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import com.intellij.openapi.util.TextRange
import com.intellij.internal.statistic.UsageTrigger


/**
 * Nikolay.Tropin
 * 6/27/13
 */
class ScalaIntroduceFieldFromExpressionHandler extends ScalaIntroduceFieldHandlerBase {
  def invoke(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int) {
    try {
      UsageTrigger.trigger(ScalaBundle.message("introduce.field.id"))
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      ScalaRefactoringUtil.checkFile(file, project, editor, REFACTORING_NAME)

      val (expr: ScExpression, types: Array[ScType]) = getExpression(project, editor, file, startOffset, endOffset).
              getOrElse(showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression"), project, editor, REFACTORING_NAME))

      afterClassChoosing[ScExpression](expr, types, project, editor, file, "Choose class for Introduce Field") {
        convertExpressionToField
      }
    }
    catch {
      case _: IntroduceException => return
    }
  }

  override def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    val canBeIntroduced: (ScExpression) => Boolean = ScalaRefactoringUtil.checkCanBeIntroduced(_)
    ScalaRefactoringUtil.afterExpressionChoosing(project, editor, file, dataContext, REFACTORING_NAME, canBeIntroduced) {
      ScalaRefactoringUtil.trimSpacesAndComments(editor, file)
      invoke(project, editor, file, editor.getSelectionModel.getSelectionStart, editor.getSelectionModel.getSelectionEnd)
    }
  }

  override def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext) {
    //nothing
  }

  def convertExpressionToField(ifc: IntroduceFieldContext[ScExpression]) {

    ScalaRefactoringUtil.checkCanBeIntroduced(ifc.element, showErrorMessage(_, ifc.project, ifc.editor, REFACTORING_NAME))

    def runWithDialog() {
      val settings = new IntroduceFieldSettings(ifc)
      if (!settings.canBeInitInDeclaration && !settings.canBeInitLocally) {
        ScalaRefactoringUtil.showErrorMessage("Cannot create field from this expression", ifc.project, ifc.editor,
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
    val checkAnchor: PsiElement = anchorForNewDeclaration(expression, occurrencesToReplace, aClass)
    if (checkAnchor == null)
      ScalaRefactoringUtil.showErrorMessage("Cannot create find place for the new field", ifc.project, ifc.editor, ScalaBundle.message("introduce.field.title"))
    val manager = aClass.getManager
    val name = settings.name
    val typeName = Option(settings.scType).map(_.canonicalText).getOrElse("")
    val replacedOccurences = ScalaRefactoringUtil.replaceOccurences(occurrencesToReplace, name, ifc.file, ifc.editor)

    val anchor = anchorForNewDeclaration(expression, replacedOccurences, aClass)
    val initInDecl = settings.initInDeclaration
    var createdDeclaration: PsiElement = null
    if (initInDecl) {
      createdDeclaration = ScalaPsiElementFactory
              .createDeclaration(name, typeName, settings.defineVar, expression, manager)
    } else {
      val underscore = ScalaPsiElementFactory.createExpressionFromText("_", manager)
      createdDeclaration = ScalaPsiElementFactory
              .createDeclaration(name, typeName, settings.defineVar, underscore, manager)

      anchorForInitializer(replacedOccurences, ifc.file) match {
        case Some(anchorForInit) =>
          val parent = anchorForInit.getParent
          val assignStmt = ScalaPsiElementFactory.createExpressionFromText(s"$name = ${expression.getText}", manager)
          parent.addBefore(assignStmt, anchorForInit)
          parent.addBefore(ScalaPsiElementFactory.createNewLineNode(manager, "\n").getPsi, anchorForInit)
        case None => throw new IntroduceException

      }
    }

    import ScalaApplicationSettings.VisibilityLevel
    settings.visibilityLevel match {
      case VisibilityLevel.DEFAULT =>
      case VisibilityLevel.PRIVATE => createdDeclaration.asInstanceOf[ScMember].setModifierProperty("private", value = true)
      case VisibilityLevel.PROTECTED => createdDeclaration.asInstanceOf[ScMember].setModifierProperty("protected", value = true)
    }


    val parent = anchor.getParent
    lazy val document: Document = ifc.editor.getDocument
    parent match {
      case ed: ScEarlyDefinitions if onOneLine(document, ed.getTextRange) =>
        def isBlockStmtOrMember(elem: PsiElement) = elem != null && (elem.isInstanceOf[ScBlockStatement] || elem.isInstanceOf[ScMember])
        var declaration = createdDeclaration.getText
        if (isBlockStmtOrMember(anchor)) declaration += "; "
        if (isBlockStmtOrMember(anchor.getPrevSibling)) declaration = "; " + declaration
        document.insertString(anchor.getTextRange.getStartOffset, declaration)
        PsiDocumentManager.getInstance(ifc.project).commitDocument(document)
      case _ =>
        createdDeclaration = parent.addBefore(createdDeclaration, anchor)
        parent.addBefore(ScalaPsiElementFactory.createNewLineNode(manager, "\n").getPsi, anchor)
    }
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
    clazz.extendsBlock match {
      case ScExtendsBlock.TemplateBody(body) if body.isAncestorOf(elem) => true
      case ScExtendsBlock.EarlyDefinitions(earlyDef)
        if ScalaRefactoringUtil.inSuperConstructor(elem, clazz) || earlyDef.isAncestorOf(elem) => true
      case _ => false
    }
  }

  private def onOneLine(document: Document, range: TextRange): Boolean = {
    document.getLineNumber(range.getStartOffset) == document.getLineNumber(range.getEndOffset)
  }

}
