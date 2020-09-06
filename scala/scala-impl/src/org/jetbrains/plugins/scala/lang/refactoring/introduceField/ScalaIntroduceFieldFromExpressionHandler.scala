package org.jetbrains.plugins.scala
package lang.refactoring.introduceField

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.{Document, Editor}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiFile}
import com.intellij.refactoring.HelpID
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.refactoring._
import org.jetbrains.plugins.scala.lang.refactoring.introduceField.ScalaIntroduceFieldHandlerBase._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil._
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}


/**
 * Nikolay.Tropin
 * 6/27/13
 */
class ScalaIntroduceFieldFromExpressionHandler extends ScalaIntroduceFieldHandlerBase {

  private var occurrenceHighlighters = Seq.empty[RangeHighlighter]

  def invoke(file: PsiFile, startOffset: Int, endOffset: Int)
            (implicit project: Project, editor: Editor): Unit = {
    try {
      Stats.trigger(FeatureKey.introduceField)

      trimSpacesAndComments(editor, file)
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      writableScalaFile(file, REFACTORING_NAME)

      val (expr, types) = getExpressionWithTypes(file, startOffset, endOffset).getOrElse {
        showErrorHint(ScalaBundle.message("cannot.refactor.not.expression"))
        return
      }

      afterClassChoosing[ScExpression](expr, types, project, editor, file, ScalaBundle.message("choose.class.for.introduce.field")) {
        convertExpressionToField
      }
    }
    catch {
      case _: IntroduceException =>
    }
  }


  override def invoke(file: PsiFile)
                     (implicit project: Project, editor: Editor, dataContext: DataContext): Unit = {
    file.findScalaLikeFile.foreach {
      scalaFile =>
        afterExpressionChoosing(scalaFile, REFACTORING_NAME) {
          invoke(scalaFile, editor.getSelectionModel.getSelectionStart, editor.getSelectionModel.getSelectionEnd)
        }
    }
  }

  def convertExpressionToField(ifc: IntroduceFieldContext[ScExpression]): Unit = {
    implicit val project: Project = ifc.project
    implicit val editor: Editor = ifc.editor

    cannotBeIntroducedReason(ifc.element) match {
      case Some(message) =>
        //noinspection ReferencePassedToNls
        showErrorHint(message)
      case _ =>
        val settings = new IntroduceFieldSettings(ifc)
        if (settings.canBeInitInDeclaration || settings.canBeInitLocally) {
          if (getDialog(ifc, settings).isOK) {
            runRefactoring(ifc, settings)
          }
        } else {
          showErrorHint(ScalaBundle.message("cannot.create.field.from.this.expression"))
        }
    }
  }

  private def runRefactoringInside(ifc: IntroduceFieldContext[ScExpression], settings: IntroduceFieldSettings[ScExpression]): Unit = {
    implicit val project: Project = ifc.project
    implicit val editor: Editor = ifc.editor

    val expression = expressionToIntroduce(ifc.element)
    val mainOcc = ifc.occurrences.filter(_.getStartOffset == editor.getSelectionModel.getSelectionStart)
    val occurrencesToReplace = if (settings.replaceAll) ifc.occurrences else mainOcc
    val aClass = ifc.aClass
    val checkAnchor: PsiElement = anchorForNewDeclaration(expression, occurrencesToReplace, aClass)
    if (checkAnchor == null) {
      showErrorHint(ScalaBundle.message("cannot.find.place.for.the.new.field"))
      return
    }
    implicit val projectContext: ProjectContext = aClass.projectContext
    val name = settings.name
    val typeName = Option(settings.scType).map(_.canonicalCodeText).getOrElse("")
    val replacedOccurences = replaceOccurrences(occurrencesToReplace, name, ifc.file)

    val anchor = anchorForNewDeclaration(expression, replacedOccurences, aClass)
    val initInDecl = settings.initInDeclaration
    var createdDeclaration: PsiElement = null
    if (initInDecl) {
      createdDeclaration = createDeclaration(name, typeName, settings.defineVar, expression)
    } else {
      val underscore = createExpressionFromText("_")
      createdDeclaration = createDeclaration(name, typeName, settings.defineVar, underscore)

      anchorForInitializer(replacedOccurences, ifc.file) match {
        case Some(anchorForInit) =>
          val parent = anchorForInit.getParent
          val assignStmt = createExpressionFromText(s"$name = ${expression.getText}")
          parent.addBefore(assignStmt, anchorForInit)
          parent.addBefore(createNewLine(), anchorForInit)
        case None => throw new IntroduceException

      }
    }

    settings.visibilityLevel match {
      case "" =>
      case other =>
        val modifier = createModifierFromText(other)
        createdDeclaration.asInstanceOf[ScMember].getModifierList.add(modifier)
    }

    lazy val document: Document = editor.getDocument

    anchor match {
      case (_: ScTemplateParents) childOf (extBl: ScExtendsBlock) =>
        val earlyDef = extBl.addEarlyDefinitions()
        createdDeclaration = earlyDef.addAfter(createdDeclaration, earlyDef.getFirstChild)
      case _ childOf (ed: ScEarlyDefinitions) if onOneLine(document, ed.getTextRange) =>
        def isBlockStmtOrMember(elem: PsiElement) = elem != null && (elem.isInstanceOf[ScBlockStatement] || elem.isInstanceOf[ScMember])
        var declaration = createdDeclaration.getText
        if (isBlockStmtOrMember(anchor)) declaration += "; "
        if (isBlockStmtOrMember(anchor.getPrevSibling)) declaration = "; " + declaration
        document.insertString(anchor.getTextRange.getStartOffset, declaration)
        PsiDocumentManager.getInstance(project).commitDocument(document)
      case _ childOf parent =>
        createdDeclaration = parent.addBefore(createdDeclaration, anchor)
        parent.addBefore(createNewLine(), anchor)
    }

    ScalaPsiUtil.adjustTypes(createdDeclaration)
  }

  def runRefactoring(ifc: IntroduceFieldContext[ScExpression],
                     settings: IntroduceFieldSettings[ScExpression]): Unit = {
    executeWriteActionCommand(REFACTORING_NAME) {
      runRefactoringInside(ifc, settings)
    }(ifc.project)
    ifc.editor.getSelectionModel.removeSelection()
  }

  protected def getDialog(ifc: IntroduceFieldContext[ScExpression], settings: IntroduceFieldSettings[ScExpression]): ScalaIntroduceFieldDialog = {
    val occCount = ifc.occurrences.length
    // Add occurrences highlighting
    if (occCount > 1)
      occurrenceHighlighters = highlightOccurrences(ifc.project, ifc.occurrences, ifc.editor)

    val dialog = new ScalaIntroduceFieldDialog(ifc, settings)
    dialog.show()
    if (!dialog.isOK) {
      if (occCount > 1) {
        occurrenceHighlighters.foreach(_.dispose())
        occurrenceHighlighters = Seq.empty
      }
    }
    dialog
  }

  protected override def isSuitableClass(elem: PsiElement, clazz: ScTemplateDefinition): Boolean = elem != clazz

  private def onOneLine(document: Document, range: TextRange): Boolean = {
    document.getLineNumber(range.getStartOffset) == document.getLineNumber(range.getEndOffset)
  }

  private def showErrorHint(@Nls message: String)
                           (implicit project: Project, editor: Editor): Unit = {
    ScalaRefactoringUtil.showErrorHint(message, REFACTORING_NAME, HelpID.INTRODUCE_FIELD)
  }

}
