package org.jetbrains.plugins.scala
package lang
package refactoring
package introduceVariable


import org.jetbrains.plugins.scala.util.ScalaUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.{Computable, TextRange, Pass}
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.{xml => _, _}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.ui.ConflictsDialog
import com.intellij.refactoring.RefactoringActionHandler
import java.util.regex.{Pattern, Matcher}
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

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.06.2008
 */

class ScalaIntroduceVariableHandler extends RefactoringActionHandler with ConflictsReporter {
  val REFACTORING_NAME = ScalaBundle.message("introduce.variable.title")
  var deleteOccurrence = false

  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    def invokes() {
      val lineText = ScalaRefactoringUtil.getLineText(editor)
      if (editor.getSelectionModel.getSelectedText != null &&
              lineText != null && editor.getSelectionModel.getSelectedText.trim == lineText.trim) deleteOccurrence = true
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
      val occurrencesAll: Array[TextRange] = ScalaRefactoringUtil.getOccurrences(ScalaRefactoringUtil.unparExpr(expr), fileEncloser)
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
                  if (isInplaceAvailable(editor)) {
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
        if (isInplaceAvailable(editor)) {
          OccurrencesChooser.simpleChooser[ScExpression](editor).showChooser(expr, allExpressionsList, callback)
        }
        else {
          callback.pass(OccurrencesChooser.ReplaceChoice.ALL)
        }
      }

      if (isInplaceAvailable(editor)) runInplace()
      else runWithDialog()

    }

    catch {
      case _: IntroduceException => return
    }
  }

  def isInplaceAvailable(editor: Editor): Boolean =
    editor.getSettings.isVariableInplaceRenameEnabled && !ApplicationManager.getApplication.isUnitTestMode

  //returns ScDeclaredElementsHolder or ScEnumerator
  def runRefactoringInside(startOffset: Int, endOffset: Int, file: PsiFile, editor: Editor, expression_ : ScExpression,
                           occurrences_ : Array[TextRange], varName: String, varType: ScType,
                           replaceAllOccurrences: Boolean, isVariable: Boolean): PsiElement = {
    var createdDeclaration: PsiElement = null
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
    //val cursorOffset = editor.getCaretModel.getOffset
    val mainOcc = occurrences.indexWhere(_.getStartOffset == startOffset)
    val document = editor.getDocument
    var i = occurrences.length - 1
    val commonParent: PsiElement = ScalaRefactoringUtil.commonParent(file, occurrences: _*)
    val typeName = ScalaRefactoringUtil.typeNameWithImportAliases(varType, commonParent)
    val container: PsiElement =
      ScalaPsiUtil.getParentOfType(commonParent, occurrences.length == 1, classOf[ScalaFile], classOf[ScBlock],
        classOf[ScTemplateBody], classOf[ScCaseClause], classOf[ScFunctionDefinition], classOf[ScFunctionExpr])
    var needBraces = false
    var needBlockWithoutBraces = false
    var elseBranch = false
    var parExpr: ScExpression = PsiTreeUtil.getParentOfType(commonParent, classOf[ScExpression], false)
    var prev: PsiElement = if (parExpr == null) file else parExpr.getParent
    if (prev.isInstanceOf[ScArgumentExprList]) prev = prev.getParent
    var introduceEnumerator = parExpr.isInstanceOf[ScForStatement]
    var introduceEnumeratorForStmt: ScForStatement =
      if (introduceEnumerator) parExpr.asInstanceOf[ScForStatement]
      else null
    def checkEnd(prev: PsiElement, parExpr: ScExpression): Boolean = {
      var result: Option[Boolean] = None
      prev match {
        case _: ScBlock => result = Some(true)
        case _: ScFunction => needBraces = true
        case memb: ScMember if memb.getParent.isInstanceOf[ScTemplateBody] => needBraces = true
        case memb: ScMember if memb.getParent.isInstanceOf[ScEarlyDefinitions] => needBraces = true
        case ifSt: ScIfStmt if ifSt.thenBranch.getOrElse(null) == parExpr ||
                ifSt.elseBranch.getOrElse(null) == parExpr => {
          if (ifSt.elseBranch.getOrElse(null) == parExpr) elseBranch = true
          needBraces = true
        }
        case forSt: ScForStatement if forSt.body.getOrElse(null) == parExpr => needBraces = true
        case forSt: ScForStatement => {
          introduceEnumerator = true
          introduceEnumeratorForStmt = forSt
          result = Some(true)
        }
        case _: ScEnumerator | _: ScGenerator => {
          introduceEnumeratorForStmt = prev.getParent.getParent.asInstanceOf[ScForStatement]
          introduceEnumerator = true
        }
        case guard: ScGuard if guard.getParent.isInstanceOf[ScEnumerators] => {
          introduceEnumeratorForStmt = prev.getParent.getParent.asInstanceOf[ScForStatement]
          introduceEnumerator = true
        }
        case whSt: ScWhileStmt if whSt.body.getOrElse(null) == parExpr => needBraces = true
        case doSt: ScDoStmt if doSt.getExprBody.getOrElse(null) == parExpr => needBraces = true
        case finBl: ScFinallyBlock if finBl.expression.getOrElse(null) == parExpr => needBraces = true
        case fE: ScFunctionExpr =>
          needBraces = fE.getContext match {
            case be: ScBlock if be.lastExpr == Some(fE) =>
              needBlockWithoutBraces = true
              false
            case _ => true
          }
        case clause: ScCaseClause => needBraces = false
        case _ =>
      }
      result getOrElse needBraces
    }
    if (!parExpr.isInstanceOf[ScBlock] || (commonParent.isInstanceOf[ScBlock] && occurrences.length == 1))
      while (prev != null && !checkEnd(prev, parExpr) && prev.isInstanceOf[ScExpression]) {
        parExpr = prev.asInstanceOf[ScExpression]
        prev = prev.getParent
        if (prev.isInstanceOf[ScArgumentExprList]) prev = prev.getParent
      }

    if (introduceEnumerator) {
      val endoffset =
        introduceEnumeratorForStmt.enumerators.getOrElse(null).generators.apply(0).getTextRange.getEndOffset
      if (occurrences(0).getStartOffset < endoffset) introduceEnumerator = false
    }
    while (i >= 0) {
      var parentheses = 0
      val offset = occurrences(i).getStartOffset
      document.replaceString(offset, occurrences(i).getEndOffset, varName)
      val documentManager = PsiDocumentManager.getInstance(editor.getProject)
      documentManager.commitDocument(document)
      val leafIdentifier = file.findElementAt(offset)
      if (!(deleteOccurrence && replaceAllOccurrences) && leafIdentifier.getParent != null &&
              leafIdentifier.getParent.getParent.isInstanceOf[ScParenthesisedExpr]) {
        val textRange = leafIdentifier.getParent.getParent.getTextRange
        document.replaceString(textRange.getStartOffset, textRange.getEndOffset, varName)
        documentManager.commitDocument(document)
        parentheses = -2
      } else if (leafIdentifier.getParent != null && leafIdentifier.getParent.getParent.isInstanceOf[ScPostfixExpr] &&
              leafIdentifier.getParent.getParent.asInstanceOf[ScPostfixExpr].operation == leafIdentifier.getParent) {
        //This case for block argument expression
        val textRange = leafIdentifier.getParent.getTextRange
        document.replaceString(textRange.getStartOffset, textRange.getEndOffset, "(" + varName + ")")
        documentManager.commitDocument(document)
        parentheses = 2
      }
      if (i == mainOcc) {
        editor.getCaretModel.moveToOffset(offset + parentheses + varName.length)
      }
      if (i == 0) {
        //from here we must to end changing document, only Psi operations (because document will be locked)
        if (introduceEnumerator) {
          val parent: ScEnumerators = introduceEnumeratorForStmt.
                  enumerators.getOrElse(null)
          var needSemicolon = false
          var sibling: PsiElement = parent
          while (sibling != null) {
            sibling.getNode.getElementType match {
              case ScalaTokenTypes.tLBRACE => sibling = null
              case ScalaTokenTypes.tLPARENTHESIS =>
                needSemicolon = true
                sibling = null
              case _ => sibling = sibling.getPrevSibling
            }
          }
          createdDeclaration = ScalaPsiElementFactory.createEnumerator(varName, ScalaRefactoringUtil.unparExpr(expression), file.getManager, typeName)
          var elem = file.findElementAt(occurrences(0).getStartOffset + (if (needBraces) 1 else 0) + parentheses)
          while (elem != null && elem.getParent != parent) elem = elem.getParent
          if (elem != null) {
            if (needSemicolon) {
              needSemicolon = true
              sibling = elem.getPrevSibling
              while (sibling != null && sibling.getText.trim == "") sibling = sibling.getPrevSibling
              if (sibling != null && sibling.getText.endsWith(";")) needSemicolon = false
              createdDeclaration = parent.addBefore(createdDeclaration, parent.addBefore(ScalaPsiElementFactory.createSemicolon(parent.getManager), elem))
              if (needSemicolon) {
                parent.addBefore(ScalaPsiElementFactory.createSemicolon(parent.getManager), createdDeclaration)
              }
            } else {
              needSemicolon = true
              sibling = elem.getPrevSibling
              if (sibling.getText.indexOf('\n') != -1) needSemicolon = false
              createdDeclaration = parent.addBefore(createdDeclaration, elem)
              parent.addBefore(ScalaPsiElementFactory.createNewLineNode(elem.getManager).getPsi, elem)
              if (needSemicolon) {
                parent.addBefore(ScalaPsiElementFactory.createNewLineNode(parent.getManager).getPsi, createdDeclaration)
              }
            }
          }
        } else {
          if (needBraces && parExpr != null && !parExpr.isValid && prev != null && prev.isValid) {
            parExpr = {
              prev match {
                case fun: ScFunctionDefinition => fun.body.getOrElse(null)
                case vl@ScPatternDefinition.expr(expr) => expr
                case vr@ScVariableDefinition.expr(expr) => expr
                case ifSt: ScIfStmt if elseBranch => ifSt.elseBranch.getOrElse(null)
                case ifSt: ScIfStmt => ifSt.thenBranch.getOrElse(null)
                case whSt: ScWhileStmt => whSt.body.getOrElse(null)
                case doSt: ScDoStmt => doSt.getExprBody.getOrElse(null)
                case fE: ScFunctionExpr => fE.result.getOrElse(null)
                case forSt: ScForStatement => forSt.body.getOrElse(null)
                case clause: ScCaseClause => clause.expr.getOrElse(null)
                case _ => null
              }
            }
          } else if (needBlockWithoutBraces && parExpr != null && parExpr.isValid && parExpr.isInstanceOf[ScFunctionExpr]) {
            parExpr match {
              case f: ScFunctionExpr =>
                f.result match {
                  case Some(res) => parExpr = res
                  case _ => needBlockWithoutBraces = false
                }
              case _ => needBlockWithoutBraces = false
            }
          } else needBlockWithoutBraces = false
          if (needBraces && parExpr != null && parExpr.isValid) {
            parExpr = parExpr.replaceExpression(ScalaPsiElementFactory.createExpressionFromText("{" + parExpr.getText + "}", file.getManager), removeParenthesis = false)
          } else if (needBlockWithoutBraces && parExpr != null && parExpr.isValid) {
            val fromText = ScalaPsiElementFactory.createBlockExpressionWithoutBracesFromText(parExpr.getText, file.getManager)
            if (fromText != null)
              parExpr = parExpr.replaceExpression(fromText, removeParenthesis = false)
          }
          val parent = if ((needBraces || needBlockWithoutBraces) && parExpr != null && parExpr.isValid) parExpr
          else container
          createdDeclaration = ScalaPsiElementFactory.createDeclaration(varName, typeName, isVariable,
            ScalaRefactoringUtil.unparExpr(expression), file.getManager)
          var elem = file.findElementAt(occurrences(0).getStartOffset + (if (needBraces) 1 else 0) + parentheses / 2)
          while (elem != null && elem.getParent != parent) elem = elem.getParent
          if (elem != null) {
            createdDeclaration = parent.addBefore(createdDeclaration, elem).asInstanceOf[ScMember]
            parent.addBefore(ScalaPsiElementFactory.createNewLineNode(elem.getManager, "\n").getPsi, elem)
          }
          if (deleteOccurrence && !replaceAllOccurrences) {
            elem = createdDeclaration.getNextSibling
            while (elem != null && elem.getText.trim == "") elem = elem.getNextSibling
            if (elem != null) {
              elem.getParent.getNode.removeChild(elem.getNode)
              val element = createdDeclaration.getNextSibling
              if (element.getText.trim == "") {
                val nl = Pattern.compile("\n", Pattern.LITERAL).matcher(element.getText).replaceFirst(Matcher.quoteReplacement(""))
                if (nl.replace(" ", "") != "") {
                  element.replace(ScalaPsiElementFactory.createNewLineNode(element.getManager, nl).getPsi)
                } else {
                  element.getParent.getNode.removeChild(element.getNode)
                }
              }
            }
            editor.getCaretModel.moveToOffset(createdDeclaration.getTextRange.getEndOffset)
          }
        }
      }
      i = i - 1
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