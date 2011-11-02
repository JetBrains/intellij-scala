package org.jetbrains.plugins.scala
package lang
package refactoring
package introduceVariable


import org.jetbrains.plugins.scala.util.ScalaUtils
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.{xml => _, _}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.ui.ConflictsDialog
import com.intellij.refactoring.{HelpID, RefactoringActionHandler}
import java.util.regex.{Pattern, Matcher}

import lexer.ScalaTokenTypes
import namesSuggester.NameSuggester
import psi.api.base.patterns.ScCaseClause
import psi.api.ScalaFile
import psi.api.statements._
import psi.api.toplevel.ScEarlyDefinitions
import psi.api.toplevel.templates.ScTemplateBody
import psi.ScalaPsiUtil
import psi.types.ScType
import psi.api.expr._
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import psi.api.toplevel.typedef.ScMember
import refactoring.util.ScalaRefactoringUtil.IntroduceException
import com.intellij.refactoring.util.CommonRefactoringUtil
import refactoring.util.{ScalaVariableValidator, ConflictsReporter, ScalaRefactoringUtil}
import org.jetbrains.plugins.scala.extensions._
import collection.mutable.HashSet
import psi.types.result.TypingContext
import psi.impl.expr.ScBlockImpl

/**
* User: Alexander Podkhalyuzin
* Date: 23.06.2008
*/

class ScalaIntroduceVariableHandler extends RefactoringActionHandler with ConflictsReporter {
  val REFACTORING_NAME = ScalaBundle.message("introduce.variable.title")
  var deleteOccurrence = false;

  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    def invokes() {
      val lineText = ScalaRefactoringUtil.getLineText(editor)
      if (editor.getSelectionModel.getSelectedText != null &&
                lineText != null && editor.getSelectionModel.getSelectedText.trim == lineText.trim) deleteOccurrence = true
      ScalaRefactoringUtil.trimSpacesAndComments(editor, file)
      invoke(project, editor, file, editor.getSelectionModel.getSelectionStart, editor.getSelectionModel.getSelectionEnd)
    }
    ScalaRefactoringUtil.invokeRefactoring(project, editor, file, dataContext, "Introduce Variable", invokes _)
  }

  def invoke(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int) {
    try {
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      if (!file.isInstanceOf[ScalaFile])
        showErrorMessage(ScalaBundle.message("only.for.scala"), project, editor)

      if (!ScalaRefactoringUtil.ensureFileWritable(project, file))
        showErrorMessage(ScalaBundle.message("file.is.not.writable"), project, editor)

      val (expr: ScExpression, typez: ScType) = ScalaRefactoringUtil.getExpression(project, editor, file, startOffset, endOffset).
              getOrElse(showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression"), project, editor))
      val types = new HashSet[ScType]

      if (typez != psi.types.Unit) types += typez
      expr.getTypeWithoutImplicits(TypingContext.empty).foreach(types += _)
      expr.getTypeIgnoreBaseType(TypingContext.empty).foreach(types += _)
      if (typez == psi.types.Unit) types += typez

      expr.getParent match {
        case inf: ScInfixExpr if inf.operation == expr => showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression"), project, editor)
        case post: ScPostfixExpr if post.operation == expr => showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression"), project, editor)
        case _: ScGenericCall => showErrorMessage(ScalaBundle.message("connot.refactor.under.generic.call"), project, editor)
        case _ if expr.isInstanceOf[ScConstrExpr] => showErrorMessage(ScalaBundle.message("cannot.refactor.constr.expression"), project, editor)
        case _ =>
      }
      val guard: ScGuard = PsiTreeUtil.getParentOfType(expr, classOf[ScGuard])
      if (guard != null && guard.getParent.isInstanceOf[ScCaseClause]) showErrorMessage(ScalaBundle.message("cannot.refactor.guard"), project, editor)

      val fileEncloser = if (file.asInstanceOf[ScalaFile].isScriptFile()) file
      else {
        var res: PsiElement = file.findElementAt(startOffset)
        while (!res.isInstanceOf[ScFunction] && res.getParent != null &&
                !res.getParent.isInstanceOf[ScTemplateBody] &&
                !res.getParent.isInstanceOf[ScEarlyDefinitions] &&
                res != file) res = res.getParent
        if (res == null) {
          for (child <- file.getChildren) {
            val textRange: TextRange = child.getTextRange
            if (textRange.contains(startOffset)) res = child
          }
        }
        res
      }
      val occurrences: Array[TextRange] = ScalaRefactoringUtil.getOccurrences(ScalaRefactoringUtil.unparExpr(expr), fileEncloser)
      // Getting settings
      val elemSeq = (for (occurence <- occurrences) yield file.findElementAt(occurence.getStartOffset)).toSeq ++
         (for (occurence <- occurrences) yield file.findElementAt(occurence.getEndOffset - 1)).toSeq
      val commonParent: PsiElement = PsiTreeUtil.findCommonParent(elemSeq: _*)
      val container: PsiElement = Option(commonParent).flatMap(_.scopes.toStream.headOption).orNull
      val commonParentOne = PsiTreeUtil.findCommonParent(file.findElementAt(startOffset), file.findElementAt(endOffset - 1))
      val containerOne = Option(commonParentOne).flatMap(_.scopes.toStream.headOption).orNull
      val validator = new ScalaVariableValidator(this, project, expr, occurrences, container, containerOne)
      val dialog = getDialog(project, editor, expr, types.toArray, occurrences, false, validator)
      if (!dialog.isOK) return

      val varName: String = dialog.getEnteredName
      val varType: ScType = dialog.getSelectedType
      val isVariable: Boolean = dialog.isDeclareVariable
      val replaceAllOccurrences: Boolean = dialog.isReplaceAllOccurrences
      runRefactoring(startOffset, endOffset, file, editor, expr, occurrences, varName, varType, replaceAllOccurrences, isVariable)
    }
    catch {
      case _: IntroduceException => return
    }
  }

  def runRefactoringInside(startOffset: Int, endOffset: Int, file: PsiFile, editor: Editor, expression_ : ScExpression,
                           occurrences_ : Array[TextRange], varName: String, varType: ScType,
                           replaceAllOccurrences: Boolean, isVariable: Boolean) {
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
    val mainOcc = occurrences.findIndexOf((occ: TextRange) =>
            occ.getStartOffset == startOffset)
    val document = editor.getDocument
    var i = occurrences.length - 1
    val elemSeq = (for (occurence <- occurrences) yield file.findElementAt(occurence.getStartOffset)).toSeq ++
      (for (occurence <- occurrences) yield file.findElementAt(occurence.getEndOffset - 1)).toSeq
    val commonParent: PsiElement = PsiTreeUtil.findCommonParent(elemSeq: _*)
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
      prev match {
        case _: ScBlock => return true
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
          return true
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
      needBraces
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
      val leaf = file.findElementAt(offset)
      if (!(deleteOccurrence && replaceAllOccurrences) && leaf.getParent != null &&
        leaf.getParent.getParent.isInstanceOf[ScParenthesisedExpr]) {
        val textRange = leaf.getParent.getParent.getTextRange
        document.replaceString(textRange.getStartOffset, textRange.getEndOffset, varName)
        documentManager.commitDocument(document)
        parentheses = -2
      } else if (leaf.getParent != null && leaf.getParent.getParent.isInstanceOf[ScPostfixExpr] &&
              leaf.getParent.getParent.asInstanceOf[ScPostfixExpr].operation == leaf.getParent) {
        //This case for block argument expression
        val textRange = leaf.getParent.getTextRange
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
          var createStmt: PsiElement =
            ScalaPsiElementFactory.createEnumerator(varName, ScalaRefactoringUtil.unparExpr(expression),
              file.getManager)
          var elem = file.findElementAt(occurrences(0).getStartOffset + (if (needBraces) 1 else 0) + parentheses)
          while (elem != null && elem.getParent != parent) elem = elem.getParent
          if (elem != null) {
            if (needSemicolon) {
              needSemicolon = true
              sibling = elem.getPrevSibling
              while (sibling != null && sibling.getText.trim == "") sibling = sibling.getPrevSibling
              if (sibling != null && sibling.getText.endsWith(";")) needSemicolon = false
              createStmt = parent.addBefore(createStmt, parent.addBefore(ScalaPsiElementFactory.
                      createSemicolon(parent.getManager),
                elem))
              if (needSemicolon) {
                parent.addBefore(ScalaPsiElementFactory.
                        createSemicolon(parent.getManager), createStmt)
              }
            } else {
              needSemicolon = true
              sibling = elem.getPrevSibling
              if (sibling.getText.indexOf('\n') != -1) needSemicolon = false
              createStmt = parent.addBefore(createStmt, elem)
              parent.addBefore(ScalaPsiElementFactory.createNewLineNode(elem.getManager).getPsi, elem)
              if (needSemicolon) {
                parent.addBefore(ScalaPsiElementFactory.
                        createNewLineNode(parent.getManager).getPsi, createStmt)
              }
            }
          }
        } else {
          if (needBraces && parExpr != null && !parExpr.isValid && prev != null && prev.isValid) {
            parExpr = {
              prev match {
                case fun: ScFunctionDefinition => fun.body.getOrElse(null)
                case vl: ScPatternDefinition => vl.expr
                case vr: ScVariableDefinition => vr.expr
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
            parExpr = parExpr.replaceExpression(ScalaPsiElementFactory.createExpressionFromText("{" + parExpr.getText + "}", file.getManager), false)
          } else if (needBlockWithoutBraces && parExpr != null && parExpr.isValid) {
            val fromText = ScalaPsiElementFactory.createBlockExpressionWithoutBracesFromText(parExpr.getText, file.getManager)
            if (fromText != null)
              parExpr = parExpr.replaceExpression(fromText, false)
          }
          val parent = if ((needBraces || needBlockWithoutBraces) && parExpr != null && parExpr.isValid) parExpr
                       else container
          var createStmt = ScalaPsiElementFactory.createDeclaration(varType, varName, isVariable, ScalaRefactoringUtil.unparExpr(expression),
            file.getManager)
          var elem = file.findElementAt(occurrences(0).getStartOffset + (if (needBraces) 1 else 0) + parentheses)
          while (elem != null && elem.getParent != parent) elem = elem.getParent
          if (elem != null) {
            createStmt = parent.addBefore(createStmt, elem).asInstanceOf[ScMember]
            parent.addBefore(ScalaPsiElementFactory.createNewLineNode(elem.getManager, "\n").getPsi, elem)
            ScalaPsiUtil.adjustTypes(createStmt)
          }          
          if (deleteOccurrence && !replaceAllOccurrences) {
            elem = createStmt.getNextSibling
            while (elem != null && elem.getText.trim == "") elem = elem.getNextSibling
            if (elem != null) {
              elem.getParent.getNode.removeChild(elem.getNode)
              val element = createStmt.getNextSibling
              if (element.getText.trim == "") {
                val nl = Pattern.compile("\n", Pattern.LITERAL).matcher(element.getText).replaceFirst(Matcher.quoteReplacement(""))
                if (nl.replace(" ", "") != "") {element.replace(ScalaPsiElementFactory.createNewLineNode(element.getManager, nl).getPsi)} else {
                  element.getParent.getNode.removeChild(element.getNode)
                }
              }
            }
            editor.getCaretModel.moveToOffset(createStmt.getTextRange.getEndOffset)
          }
        }
      }
      i = i - 1
    }
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

    ScalaUtils.runWriteAction(runnable, editor.getProject, REFACTORING_NAME);
    editor.getSelectionModel.removeSelection()
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

  /**
   * @throws IntroduceException
   */
  def showErrorMessage(text: String, project: Project, editor: Editor): Nothing = {
    if (ApplicationManager.getApplication.isUnitTestMode) throw new RuntimeException(text)
    CommonRefactoringUtil.showErrorHint(project, editor, text, REFACTORING_NAME, HelpID.INTRODUCE_VARIABLE)
    throw new IntroduceException
  }


}