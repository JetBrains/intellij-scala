package org.jetbrains.plugins.scala
package lang
package refactoring
package introduceVariable


import org.jetbrains.plugins.scala.util.ScalaUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util._
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.{xml => _, _}
import com.intellij.refactoring.ui.ConflictsDialog
import com.intellij.refactoring.RefactoringActionHandler
import java.util.LinkedHashSet
import lexer.ScalaTokenTypes
import namesSuggester.NameSuggester
import psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import psi.api.expr._
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import refactoring.util.ScalaRefactoringUtil.{IntroduceException, showErrorMessage}
import refactoring.util.{ConflictsReporter, ScalaRefactoringUtil}
import com.intellij.refactoring.introduce.inplace.OccurrencesChooser
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaVariableValidator
import com.intellij.psi.util.PsiTreeUtil
import extensions.childOf
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScClassParents, ScExtendsBlock}
import com.intellij.internal.statistic.UsageTrigger


/**
 * User: Alexander Podkhalyuzin
 * Date: 23.06.2008
 */

class ScalaIntroduceVariableHandler extends RefactoringActionHandler with ConflictsReporter {
  val REFACTORING_NAME = ScalaBundle.message("introduce.variable.title")

  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    val canBeIntroduced: ScExpression => Boolean = ScalaRefactoringUtil.checkCanBeIntroduced(_)
    ScalaRefactoringUtil.afterExpressionChoosing(project, editor, file, dataContext, "Introduce Parameter", canBeIntroduced) {
      ScalaRefactoringUtil.trimSpacesAndComments(editor, file)
      invoke(project, editor, file, editor.getSelectionModel.getSelectionStart, editor.getSelectionModel.getSelectionEnd)
    }
  }

  def invoke(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int) {

    try {
      UsageTrigger.trigger(ScalaBundle.message("introduce.variable.id"))

      PsiDocumentManager.getInstance(project).commitAllDocuments()
      ScalaRefactoringUtil.checkFile(file, project, editor, REFACTORING_NAME)

      val (expr: ScExpression, types: Array[ScType]) = ScalaRefactoringUtil.getExpression(project, editor, file, startOffset, endOffset).
              getOrElse(showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression"), project, editor, REFACTORING_NAME))

      ScalaRefactoringUtil.checkCanBeIntroduced(expr, showErrorMessage(_, project, editor, REFACTORING_NAME))

      val fileEncloser = ScalaRefactoringUtil.fileEncloser(startOffset, file)
      val occurrences: Array[TextRange] = ScalaRefactoringUtil.getOccurrenceRanges(ScalaRefactoringUtil.unparExpr(expr), fileEncloser)
      val validator = ScalaVariableValidator(this, project, editor, file, expr, occurrences)

      def runWithDialog() {
        val dialog = getDialog(project, editor, expr, types, occurrences, declareVariable = false, validator)
        if (!dialog.isOK) return
        val varName: String = dialog.getEnteredName
        val varType: ScType = dialog.getSelectedType
        val isVariable: Boolean = dialog.isDeclareVariable
        val replaceAllOccurrences: Boolean = dialog.isReplaceAllOccurrences
        runRefactoring(startOffset, endOffset, file, editor, expr, occurrences, varName, varType, replaceAllOccurrences, isVariable)
      }

      def runInplace() {
        val allExpressions: Array[ScExpression] = occurrences map {
          r => ScalaRefactoringUtil.getExpression(project, editor, file, r.getStartOffset, r.getEndOffset)
        } collect { case Some((expression, _)) => expression}
        import scala.collection.JavaConversions.asJavaCollection
        val allExpressionsList = new java.util.ArrayList[ScExpression](allExpressions.toIterable)

        val callback = new Pass[OccurrencesChooser.ReplaceChoice] {
          def pass(replaceChoice: OccurrencesChooser.ReplaceChoice) {
            val replaceAll = OccurrencesChooser.ReplaceChoice.NO != replaceChoice
            val suggestedNames: Array[String] = NameSuggester.suggestNames(expr, validator)
            import scala.collection.JavaConversions.asJavaCollection
            val suggestedNamesSet = new LinkedHashSet[String](suggestedNames.toIterable)
            val asVar = ScalaApplicationSettings.getInstance().INTRODUCE_VARIABLE_IS_VAR
            val forceInferType = expr match {
              case _: ScFunctionExpr => Some(true)
              case _ => None
            }
            val needExplicitType = forceInferType.getOrElse(ScalaApplicationSettings.getInstance().INTRODUCE_VARIABLE_EXPLICIT_TYPE)
            val selectedType = if (needExplicitType) types(0) else null
            val introduceRunnable: Computable[PsiElement] =
              introduceVariable(startOffset, endOffset, file, editor, expr, occurrences, suggestedNames(0), selectedType,
                replaceAll, asVar)
            CommandProcessor.getInstance.executeCommand(project, new Runnable {
              def run() {
                val newDeclaration: PsiElement = ApplicationManager.getApplication.runWriteAction(introduceRunnable)
                val namedElement: PsiNamedElement = newDeclaration match {
                  case holder: ScDeclaredElementsHolder => holder.declaredElements.headOption.orNull
                  case enum: ScEnumerator => enum.pattern.bindings.headOption.orNull
                  case _ => null
                }
                if (namedElement != null && namedElement.isValid) {
                  editor.getCaretModel.moveToOffset(namedElement.getTextOffset)
                  editor.getSelectionModel.removeSelection()
                  if (ScalaRefactoringUtil.isInplaceAvailable(editor)) {
                    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
                    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument)
                    val checkedExpr = if (expr.isValid) expr else null
                    val variableIntroducer =
                      new ScalaInplaceVariableIntroducer(project, editor, checkedExpr, types, namedElement,
                        REFACTORING_NAME, replaceAll, asVar, forceInferType)
                    variableIntroducer.performInplaceRefactoring(suggestedNamesSet)
                  }
                }
              }
            }, REFACTORING_NAME, null)
          }
        }
        if (ScalaRefactoringUtil.isInplaceAvailable(editor)) {
          OccurrencesChooser.simpleChooser[ScExpression](editor).showChooser(expr, allExpressionsList, callback)
        }
        else {
          callback.pass(OccurrencesChooser.ReplaceChoice.ALL)
        }
      }

      if (ScalaRefactoringUtil.isInplaceAvailable(editor)) runInplace()
      else runWithDialog()

    }

    catch {
      case _: IntroduceException => return
    }
  }

  //returns ScDeclaredElementsHolder or ScEnumerator
  def runRefactoringInside(startOffset: Int, endOffset: Int, file: PsiFile, editor: Editor, expression_ : ScExpression,
                           occurrences_ : Array[TextRange], varName: String, varType: ScType,
                           replaceAllOccurrences: Boolean, isVariable: Boolean): PsiElement = {

    def forStmtIfIntroduceEnumerator(parExpr: PsiElement, prev: PsiElement, firstOccurenceOffset: Int): Option[ScForStatement] = {
      val result = prev match {
        case forSt: ScForStatement if forSt.body.orNull == parExpr => None
        case forSt: ScForStatement => Some(forSt)
        case _: ScEnumerator | _: ScGenerator => Option(prev.getParent.getParent.asInstanceOf[ScForStatement])
        case guard: ScGuard if guard.getParent.isInstanceOf[ScEnumerators] => Option(prev.getParent.getParent.asInstanceOf[ScForStatement])
        case _ => None
      }
      for {//check that first occurence is after first generator
        forSt <- result
        enums <- forSt.enumerators
        generator = enums.generators.apply(0)
        if firstOccurenceOffset > generator.getTextRange.getEndOffset
      } yield forSt
    }
    def addPrivateIfNotLocal(declaration: PsiElement) {
      declaration match {
        case member: ScMember if !member.isLocal =>
          member.setModifierProperty("private", value = true)
        case _ =>
      }
    }
    def replaceRangeByDeclaration(range: TextRange, element: PsiElement): PsiElement = {
      val (start, end) = (range.getStartOffset, range.getEndOffset)
      val text: String = element.getText
      val document = editor.getDocument
      document.replaceString(start, end, text)
      PsiDocumentManager.getInstance(element.getProject).commitDocument(document)
      val newEnd = start + text.length
      editor.getCaretModel.moveToOffset(newEnd)
      val decl = PsiTreeUtil.findElementOfClassAtOffset(file, start, classOf[ScMember], /*strictStart =*/false)
      lazy val enum = PsiTreeUtil.findElementOfClassAtOffset(file, start, classOf[ScEnumerator], /*strictStart =*/false)
      Option(decl).getOrElse(enum)
    }

    val revertInfo = ScalaRefactoringUtil.RevertInfo(file.getText, editor.getCaretModel.getOffset)
    editor.putUserData(ScalaIntroduceVariableHandler.REVERT_INFO, revertInfo)

    val typeName = if (varType != null) varType.canonicalText else ""
    val expression = ScalaRefactoringUtil.expressionToIntroduce(expression_)
    val isFunExpr = expression.isInstanceOf[ScFunctionExpr]

    val mainRange = new TextRange(startOffset, endOffset)
    val occurrences: Array[TextRange] = if (!replaceAllOccurrences) {
      Array[TextRange](mainRange)
    } else occurrences_
    val occCount = occurrences.length
    val mainOcc = occurrences.indexWhere(range => range.contains(mainRange) || mainRange.contains(range))

    val lineText = ScalaRefactoringUtil.getLineText(editor)
    val fastDefinition = editor.getSelectionModel.getSelectedText != null &&
            lineText != null && editor.getSelectionModel.getSelectedText.trim == lineText.trim && occCount == 1

    //changes document directly
    val replacedOccurences = ScalaRefactoringUtil.replaceOccurences(occurrences, varName, file, editor)
    //only Psi-operations after this moment
    var firstRange = replacedOccurences(0)

    val commonParent: PsiElement = ScalaRefactoringUtil.commonParent(file, replacedOccurences: _*)
    val parExpr = ScalaRefactoringUtil.findParentExpr(commonParent) match {
      case _ childOf ((block: ScBlock) childOf ((_) childOf (call: ScMethodCall)))
        if isFunExpr && occCount == 1 && block.statements.size == 1 => call
      case _ childOf ((block: ScBlock) childOf (infix: ScInfixExpr))
        if isFunExpr && occCount == 1 && block.statements.size == 1 => infix
      case expr => expr
    }
    val nextParent: PsiElement = ScalaRefactoringUtil.nextParent(parExpr, file)

    val forStmtOption = forStmtIfIntroduceEnumerator(parExpr, nextParent, firstRange.getStartOffset)
    val introduceEnumerator = forStmtOption.isDefined
    val introduceEnumeratorForStmt: ScForStatement = forStmtOption.orNull

    editor.getCaretModel.moveToOffset(replacedOccurences(mainOcc).getEndOffset)

    var createdDeclaration: PsiElement = null

    if (introduceEnumerator) {
      val parent: ScEnumerators = introduceEnumeratorForStmt.enumerators.orNull
      val inParentheses = parent.prevSiblings.toList.exists(_.getNode.getElementType == ScalaTokenTypes.tLPARENTHESIS)
      createdDeclaration = ScalaPsiElementFactory.createEnumerator(varName, ScalaRefactoringUtil.unparExpr(expression), file.getManager, typeName)
      val elem = parent.getChildren.filter(_.getTextRange.contains(firstRange)).head
      if (elem != null) {
        var needSemicolon = true
        var sibling = elem.getPrevSibling
        if (inParentheses) {
          while (sibling != null && sibling.getText.trim == "") sibling = sibling.getPrevSibling
          if (sibling != null && sibling.getText.endsWith(";")) needSemicolon = false
          createdDeclaration = parent.addBefore(createdDeclaration, parent.addBefore(ScalaPsiElementFactory.createSemicolon(parent.getManager), elem))
          if (needSemicolon) {
            parent.addBefore(ScalaPsiElementFactory.createSemicolon(parent.getManager), createdDeclaration)
          }
        } else {
          if (sibling.getText.indexOf('\n') != -1) needSemicolon = false
          createdDeclaration = parent.addBefore(createdDeclaration, elem)
          parent.addBefore(ScalaPsiElementFactory.createNewLineNode(elem.getManager).getPsi, elem)
          if (needSemicolon) {
            parent.addBefore(ScalaPsiElementFactory.createNewLineNode(parent.getManager).getPsi, createdDeclaration)
          }
        }
      }
    } else {
      createdDeclaration = ScalaPsiElementFactory.createDeclaration(varName, typeName, isVariable,
        ScalaRefactoringUtil.unparExpr(expression), file.getManager)
      if (fastDefinition) {
        createdDeclaration = replaceRangeByDeclaration(replacedOccurences(0), createdDeclaration)
      }
      else {
        object inExtendsBlock {
          def unapply(e: PsiElement): Option[ScExtendsBlock] = {
            e match {
              case extBl: ScExtendsBlock =>
                Some(extBl)
              case elem if PsiTreeUtil.getParentOfType(elem, classOf[ScClassParents]) != null =>
                PsiTreeUtil.getParentOfType(elem, classOf[ScExtendsBlock]) match {
                  case _ childOf (_: ScNewTemplateDefinition) => None
                  case extBl => Some(extBl)
                }
              case _ => None
            }
          }
        }

        var needFormatting = false
        val parent = commonParent match {
          case inExtendsBlock(extBl) =>
            needFormatting = true
            extBl.addEarlyDefinitions()
          case _ =>
            val container = ScalaRefactoringUtil.container(parExpr, file)
            val needBraces = !parExpr.isInstanceOf[ScBlock] && ScalaRefactoringUtil.needBraces(parExpr, nextParent)
            if (needBraces) {
              firstRange = firstRange.shiftRight(1)
              val replaced = parExpr.replaceExpression(ScalaPsiElementFactory.createExpressionFromText("{" + parExpr.getText + "}", file.getManager),
                removeParenthesis = false)
              replaced.getPrevSibling match {
                case ws: PsiWhiteSpace if ws.getText.contains("\n") => ws.delete()
                case _ =>
              }
              replaced
            } else container
        }
        val anchor = parent.getChildren.find(_.getTextRange.contains(firstRange)).getOrElse(parent.getLastChild)
        if (anchor != null) {
          createdDeclaration = ScalaPsiUtil.addStatementBefore(createdDeclaration.asInstanceOf[ScBlockStatement], parent, Some(anchor))
          CodeEditUtil.markToReformat(parent.getNode, needFormatting)
        } else throw new IntroduceException
      }
    }
    addPrivateIfNotLocal(createdDeclaration)
    ScalaPsiUtil.adjustTypes(createdDeclaration)
    createdDeclaration
  }

  def runRefactoring(startOffset: Int, endOffset: Int, file: PsiFile, editor: Editor, expression: ScExpression,
                     occurrences_ : Array[TextRange], varName: String, varType: ScType,
                     replaceAllOccurrences: Boolean, isVariable: Boolean) {
    val runnable = new Runnable() {
      def run() {
        runRefactoringInside(startOffset, endOffset, file, editor, expression, occurrences_, varName,
          varType, replaceAllOccurrences, isVariable) //this for better debug
      }
    }

    ScalaUtils.runWriteAction(runnable, editor.getProject, REFACTORING_NAME)
    editor.getSelectionModel.removeSelection()
  }

  def introduceVariable(startOffset: Int, endOffset: Int, file: PsiFile, editor: Editor, expression: ScExpression,
                        occurrences_ : Array[TextRange], varName: String, varType: ScType,
                        replaceAllOccurrences: Boolean, isVariable: Boolean): Computable[PsiElement] = {

    new Computable[PsiElement]() {
      def compute(): PsiElement = runRefactoringInside(startOffset, endOffset, file, editor, expression, occurrences_, varName,
        varType, replaceAllOccurrences, isVariable)
    }

  }

  def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext) {
    //nothing to do
  }

  protected def getDialog(project: Project, editor: Editor, expr: ScExpression, typez: Array[ScType],
                          occurrences: Array[TextRange], declareVariable: Boolean,
                          validator: ScalaVariableValidator): ScalaIntroduceVariableDialog = {
    // Add occurrences highlighting
    if (occurrences.length > 1)
      ScalaRefactoringUtil.highlightOccurrences(project, occurrences, editor)

    val possibleNames = NameSuggester.suggestNames(expr, validator)
    val dialog = new ScalaIntroduceVariableDialog(project, typez, occurrences.length, validator, possibleNames)
    dialog.show()
    if (!dialog.isOK) {
      if (occurrences.length > 1) {
        WindowManager.getInstance.getStatusBar(project).
                setInfo(ScalaBundle.message("press.escape.to.remove.the.highlighting"))
      }
    }

    dialog
  }

  def reportConflicts(conflicts: Array[String], project: Project): Boolean = {
    val conflictsDialog = new ConflictsDialog(project, conflicts: _*) //todo: add psi element to conflict
    conflictsDialog.show()
    conflictsDialog.isOK
  }

  def runTest(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int, replaceAll: Boolean) {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    ScalaRefactoringUtil.checkFile(file, project, editor, REFACTORING_NAME)

    val (expr: ScExpression, types: Array[ScType]) = ScalaRefactoringUtil.getExpression(project, editor, file, startOffset, endOffset).
            getOrElse(showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression"), project, editor, REFACTORING_NAME))

    ScalaRefactoringUtil.checkCanBeIntroduced(expr, showErrorMessage(_, project, editor, REFACTORING_NAME))

    val fileEncloser = ScalaRefactoringUtil.fileEncloser(startOffset, file)
    val occurrences: Array[TextRange] = ScalaRefactoringUtil.getOccurrenceRanges(ScalaRefactoringUtil.unparExpr(expr), fileEncloser)
    runRefactoring(startOffset, endOffset, file, editor, expr, occurrences, "value", types(0), replaceAll, isVariable = false)
  }

}

object ScalaIntroduceVariableHandler {
  val REVERT_INFO: Key[ScalaRefactoringUtil.RevertInfo] = new Key("RevertInfo")
}