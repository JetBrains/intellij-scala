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
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.ui.ConflictsDialog
import com.intellij.refactoring.RefactoringActionHandler
import java.util.LinkedHashSet
import lexer.ScalaTokenTypes
import namesSuggester.NameSuggester
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import psi.api.ScalaFile
import psi.api.statements._
import psi.api.toplevel.ScEarlyDefinitions
import psi.api.toplevel.templates.ScTemplateBody
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
import org.jetbrains.plugins.scala.lang.refactoring.introduceVariable.ScalaIntroduceVariableHandler.RevertInfo
import scala.annotation.tailrec

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.06.2008
 */

class ScalaIntroduceVariableHandler extends RefactoringActionHandler with ConflictsReporter {
  val REFACTORING_NAME = ScalaBundle.message("introduce.variable.title")

  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    def invokes() {
//      val lineText = ScalaRefactoringUtil.getLineText(editor)
//      if (editor.getSelectionModel.getSelectedText != null &&
//              lineText != null && editor.getSelectionModel.getSelectedText.trim == lineText.trim) deleteOccurrence = true
      ScalaRefactoringUtil.trimSpacesAndComments(editor, file)
      invoke(project, editor, file, editor.getSelectionModel.getSelectionStart, editor.getSelectionModel.getSelectionEnd)
    }
    val canBeIntroduced: ScExpression => Boolean = ScalaRefactoringUtil.checkCanBeIntroduced(_)
    ScalaRefactoringUtil.invokeRefactoring(project, editor, file, dataContext, "Introduce Parameter", invokes, canBeIntroduced)
  }

  def invoke(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int) {

    try {
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      ScalaRefactoringUtil.checkFile(file, project, editor, REFACTORING_NAME)

      val (expr: ScExpression, scType: ScType) = ScalaRefactoringUtil.getExpression(project, editor, file, startOffset, endOffset).
              getOrElse(showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression"), project, editor, REFACTORING_NAME))

      val types = ScalaRefactoringUtil.addPossibleTypes(scType, expr)

      ScalaRefactoringUtil.checkCanBeIntroduced(expr, showErrorMessage(_, project, editor, REFACTORING_NAME))

      val fileEncloser = ScalaRefactoringUtil.fileEncloser(startOffset, file)
      val occurrencesAll: Array[TextRange] = ScalaRefactoringUtil.getOccurrenceRanges(ScalaRefactoringUtil.unparExpr(expr), fileEncloser)
      val occurrences = occurrencesAll.filterNot(ScalaRefactoringUtil.isLiteralPattern(file, _))
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
            val replaces: Array[ScExpression] = if (replaceAll) allExpressions else Array(expr)
            val suggestedNames: Array[String] = NameSuggester.suggestNames(expr, validator)
            import scala.collection.JavaConversions.asJavaCollection
            val suggestedNamesSet = new LinkedHashSet[String](suggestedNames.toIterable)
            val asVar = ScalaApplicationSettings.getInstance().INTRODUCE_LOCAL_CREATE_VARIABLE
            ScalaApplicationSettings.getInstance().SPECIFY_TYPE_EXPLICITLY
            val selectedType = if (ScalaApplicationSettings.getInstance().SPECIFY_TYPE_EXPLICITLY) types(0) else null
            val introduceRunnable: Computable[PsiElement] =
              introduceVariable(startOffset, endOffset, file, editor, expr, occurrences, suggestedNames(0), selectedType,
                replaceAll, asVar)
            CommandProcessor.getInstance.executeCommand(project, new Runnable {
              def run() {
                val newDeclaration: PsiElement = ApplicationManager.getApplication.runWriteAction(introduceRunnable)
                var namedElement: PsiNamedElement = null
                newDeclaration match {
                  case holder: ScDeclaredElementsHolder if holder.declaredElements.nonEmpty =>
                    namedElement = holder.declaredElements(0)
                  case enum: ScEnumerator =>
                    namedElement = enum.pattern.bindings(0)
                }
                if (namedElement != null) {
                  editor.getCaretModel.moveToOffset(namedElement.getTextOffset)
                  editor.getSelectionModel.removeSelection()
                  if (ScalaRefactoringUtil.isInplaceAvailable(editor)) {
                    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
                    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument)
                    val checkedExpr = if (expr.isValid) expr else null
                    val variableIntroducer =
                      new ScalaInplaceVariableIntroducer(project, editor, checkedExpr, types, namedElement, replaces,
                        REFACTORING_NAME, replaceAll, asVar, false)
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
        case forSt: ScForStatement if forSt.body.getOrElse(null) == parExpr => None
        case forSt: ScForStatement => Some(forSt)
        case _: ScEnumerator | _: ScGenerator => Option(prev.getParent.getParent.asInstanceOf[ScForStatement])
        case guard: ScGuard if guard.getParent.isInstanceOf[ScEnumerators] => Option(prev.getParent.getParent.asInstanceOf[ScForStatement])
        case _ => None
      }
      for { //check that first occurence is after first generator
        forSt <- result
        enums <- forSt.enumerators
        generator = enums.generators.apply(0)
        if firstOccurenceOffset > generator.getTextRange.getEndOffset
      } yield forSt
    }

    def needNewBraces(parExpr: PsiElement, prev: PsiElement, occurrenceCount: Int): Boolean = {
      if (!parExpr.isInstanceOf[ScBlock])
        prev match {
          case _: ScBlock | _: ScTemplateBody | _: ScEarlyDefinitions | _: ScalaFile | _: ScCaseClause => false
          case _: ScFunction => true
          case memb: ScMember if memb.getParent.isInstanceOf[ScTemplateBody] => true
          case memb: ScMember if memb.getParent.isInstanceOf[ScEarlyDefinitions] => true
          case ifSt: ScIfStmt if Seq(ifSt.thenBranch, ifSt.elseBranch) contains Option(parExpr) => true
          case forSt: ScForStatement if forSt.body.getOrElse(null) == parExpr => true
          case forSt: ScForStatement => false
          case _: ScEnumerator | _: ScGenerator => false
          case guard: ScGuard if guard.getParent.isInstanceOf[ScEnumerators] => false
          case whSt: ScWhileStmt if whSt.body.getOrElse(null) == parExpr => true
          case doSt: ScDoStmt if doSt.getExprBody.getOrElse(null) == parExpr => true
          case finBl: ScFinallyBlock if finBl.expression.getOrElse(null) == parExpr => true
          case fE: ScFunctionExpr =>
            fE.getContext match {
              case be: ScBlock if be.lastExpr == Some(fE) => false
              case _ => true
            }
          case _ => false
        } else false
    }

    @tailrec
    def findParentExpr(elem: PsiElement, occurrenceCount: Int): ScExpression = {
      def checkEnd(prev: PsiElement, parExpr: ScExpression, occurrenceCount: Int): Boolean = {
        val result: Boolean = prev match {
          case _: ScBlock => true
          case forSt: ScForStatement if forSt.body.getOrElse(null) == parExpr => false //in this case needBraces == true
          case forSt: ScForStatement => true
          case _ => false
        }
        result || needNewBraces(parExpr, prev, occurrenceCount)
      }
      val expr = PsiTreeUtil.getParentOfType(elem, classOf[ScExpression], false)
      val prev = previous(expr, expr.getContainingFile)
      prev match {
        case prevExpr: ScExpression if !checkEnd(prev, expr, occurrenceCount) => findParentExpr(prevExpr, occurrenceCount)
        case prevExpr: ScExpression if checkEnd(prev, expr, occurrenceCount) => expr
        case _ => expr
      }
    }

    def previous(expr: ScExpression, file: PsiFile): PsiElement = {
      if (expr == null) file else expr.getParent match {
        case args: ScArgumentExprList => args.getParent
        case other => other
      }
    }

    val revertInfo = RevertInfo(file.getText, editor.getCaretModel.getOffset)
    editor.putUserData(ScalaIntroduceVariableHandler.REVERT_INFO, revertInfo)

    val expression = {
      def copyExpr = expression_.copy.asInstanceOf[ScExpression]
      def liftMethod = ScalaPsiElementFactory.createExpressionFromText(expression_.getText + " _", expression_.getManager)
      expression_ match {
        case ref: ScReferenceExpression => {
          ref.resolve() match {
            case fun: ScFunction if fun.paramClauses.clauses.length > 0 &&
                    fun.paramClauses.clauses.head.isImplicit => copyExpr
            case fun: ScFunction if !fun.parameters.isEmpty => liftMethod
            case meth: PsiMethod if !meth.getParameterList.getParameters.isEmpty => liftMethod
            case _ => copyExpr
          }
        }
        case _ => copyExpr
      }
    }

    val occurrences: Array[TextRange] = if (!replaceAllOccurrences) {
      Array[TextRange](new TextRange(startOffset, endOffset))
    } else occurrences_
    val occCount = occurrences.length
    val mainOcc = occurrences.indexWhere(_.getStartOffset == startOffset)

    //changes document directly
    val replacedOccurences = occurrences.reverseMap(ScalaRefactoringUtil.replaceOccurence(_, varName, file, editor)).reverse
    //only Psi-operations after this moment
    var firstRange = replacedOccurences(0).getTextRange

    val commonParent: PsiElement = ScalaRefactoringUtil.commonParent(file, replacedOccurences.map(_.getTextRange): _*)

    val parExpr: ScExpression = findParentExpr(commonParent, occCount)
    val prev: PsiElement = previous(parExpr, file)

    val forStmtOption = forStmtIfIntroduceEnumerator(parExpr, prev, firstRange.getStartOffset)
    val introduceEnumerator = forStmtOption.isDefined
    val introduceEnumeratorForStmt: ScForStatement = forStmtOption.getOrElse(null)

    editor.getCaretModel.moveToOffset(replacedOccurences(mainOcc).getTextRange.getEndOffset)

    var createdDeclaration: PsiElement = null
    val typeName = ScalaRefactoringUtil.typeNameWithImportAliases(varType, replacedOccurences(0))

    if (introduceEnumerator) {
      val parent: ScEnumerators = introduceEnumeratorForStmt.enumerators.getOrElse(null)
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
      val container: PsiElement =
        ScalaPsiUtil.getParentOfType(parExpr, occCount == 1, classOf[ScalaFile], classOf[ScBlock],
          classOf[ScTemplateBody], classOf[ScCaseClause], classOf[ScEarlyDefinitions])
      val needBraces = needNewBraces(parExpr, prev, occCount)
      val parent =
        if (needBraces) {
          firstRange = firstRange.shiftRight(1)
          parExpr.replaceExpression(ScalaPsiElementFactory.createExpressionFromText("{" + parExpr.getText + "}", file.getManager),
            removeParenthesis = false)
        } else container

      val anchor = parent.getChildren.filter(_.getTextRange.contains(firstRange)).head
      if (anchor != null) {
        createdDeclaration = parent.addBefore(createdDeclaration, anchor).asInstanceOf[ScMember]
        parent.addBefore(ScalaPsiElementFactory.createNewLineNode(anchor.getManager, "\n").getPsi, anchor)
      } else throw new IntroduceException
    }
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
}

object ScalaIntroduceVariableHandler {
  val REVERT_INFO: Key[RevertInfo] = new Key("RevertInfo")
  case class RevertInfo(fileText: String, caretOffset: Int)
}