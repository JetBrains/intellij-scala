package org.jetbrains.plugins.scala.lang.refactoring.introduceVariable


import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.lang.TokenWrapper
import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorColors}
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.{Editor, VisualPosition}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.ui.ConflictsDialog
import com.intellij.refactoring.util.RefactoringMessageDialog
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
import psi.types.{ScType, ScFunctionType}
import psi.api.expr._
import psi.api.toplevel.typedef.ScTypeDefinition
import psi.api.toplevel.typedef.ScTrait
import psi.api.toplevel.typedef.ScClass
import psi.api.base.ScReferenceElement
import collection.mutable.ArrayBuffer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiType
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiFile
import com.intellij.openapi.project.Project
import psi.api.toplevel.typedef.ScMember
import scala.util.ScalaUtils
import refactoring.util.ScalaRefactoringUtil

/**
* User: Alexander Podkhalyuzin
* Date: 23.06.2008
*/

class ScalaIntroduceVariableHandler extends RefactoringActionHandler {
  val REFACTORING_NAME = ScalaBundle.message("introduce.variable.title")
  var deleteOccurence = false;



  private def getLineText(editor: Editor): String = {
    val lineNumber = editor.getCaretModel.getLogicalPosition.line
    if (lineNumber >= editor.getDocument.getLineCount) return ""
    val caret = editor.getCaretModel.getVisualPosition
    val lineStart = editor.visualToLogicalPosition(new VisualPosition(caret.line, 0));
    val nextLineStart = editor.visualToLogicalPosition(new VisualPosition(caret.line + 1, 0))
    val start = editor.logicalPositionToOffset(lineStart)
    val end = editor.logicalPositionToOffset(nextLineStart)
    return editor.getDocument.getText.substring(start, end)
  }

  def invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
    if (!editor.getSelectionModel.hasSelection) editor.getSelectionModel.selectLineAtCaret

    val lineText = getLineText(editor)
    if (editor.getSelectionModel.getSelectedText != null &&
            lineText != null && editor.getSelectionModel.getSelectedText.trim == lineText.trim) deleteOccurence = true
    ScalaRefactoringUtil.trimSpacesAndComments(editor, file)
    invoke(project, editor, file, editor.getSelectionModel.getSelectionStart, editor.getSelectionModel.getSelectionEnd)
  }

  def invoke(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int) {
    try {
      PsiDocumentManager.getInstance(project).commitAllDocuments
      if (!file.isInstanceOf[ScalaFile])
        showErrorMessage(ScalaBundle.message("only.for.scala"), project)
      
      if (!ScalaRefactoringUtil.ensureFileWritable(project, file))
        showErrorMessage(ScalaBundle.message("file.is.not.writable"), project)

      val (expr: ScExpression, typez: ScType) = ScalaRefactoringUtil.getExpression(project, editor, file, startOffset, endOffset).
              getOrElse(showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression"), project))

      expr.getParent match {
        case inf: ScInfixExpr if inf.operation == expr => showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression"), project)
        case post: ScPostfixExpr if post.operation == expr => showErrorMessage(ScalaBundle.message("cannot.refactor.not.expression"), project)
        case _: ScGenericCall => showErrorMessage(ScalaBundle.message("connot.refactor.under.generic.call"), project)
        case _ if expr.isInstanceOf[ScConstrExpr] => showErrorMessage(ScalaBundle.message("cannot.refactor.constr.expression"), project)
        case _ =>
      }
      val guard: ScGuard = PsiTreeUtil.getParentOfType(expr, classOf[ScGuard])
      if (guard != null && guard.getParent.isInstanceOf[ScCaseClause]) showErrorMessage(ScalaBundle.message("cannot.refactor.guard"), project)

      val occurrences: Array[TextRange] = ScalaRefactoringUtil.getOccurrences(ScalaRefactoringUtil.unparExpr(expr), file) //todo:
      // Getting settings
      var validator = new ScalaVariableValidator(this, project, expr, occurrences, file)
      var dialog = getDialog(project, editor, expr, typez, occurrences, false, validator)
      if (!dialog.isOK) return

      val varName: String = dialog.getEnteredName
      var varType: ScType = dialog.getSelectedType
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
      expression_ match {
        case ref: ScReferenceExpression => {
          ref.resolve match {
            case _: ScFunction => ScalaPsiElementFactory.createExpressionFromText(
              expression_.getText + " _", expression_.getManager
              )
            case _ => expression_.copy.asInstanceOf[ScExpression]
          }
        }
        case _ => expression_.copy.asInstanceOf[ScExpression]
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
    val container: PsiElement = ScalaPsiUtil.getParentOfType(commonParent, occurrences.length == 1, classOf[ScalaFile], classOf[ScBlock],
      classOf[ScTemplateBody])
    var needBraces = false
    var elseBranch = false
    var parExpr: ScExpression = PsiTreeUtil.getParentOfType(commonParent, classOf[ScExpression], false)
    var prev: PsiElement = if (parExpr == null) file else parExpr.getParent
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
        case ifSt: ScIfStmt if ifSt.thenBranch.getOrElse(null) == parExpr || ifSt.elseBranch.getOrElse(null) == parExpr => {
          if (ifSt.elseBranch.getOrElse(null) == parExpr) elseBranch = true
          needBraces = true
        }
        case forSt: ScForStatement if forSt.expression.getOrElse(null) == parExpr => needBraces = true
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
        case whSt: ScWhileStmt if whSt.expression.getOrElse(null) == parExpr => needBraces = true
        case doSt: ScDoStmt if doSt.getExprBody.getOrElse(null) == parExpr => needBraces = true
        case finBl: ScFinallyBlock if finBl.expression.getOrElse(null) == parExpr => needBraces = true
        case fE: ScFunctionExpr => needBraces = true
        case clause: ScCaseClause => needBraces = true
        case _ =>
      }
      needBraces
    }
    if (!parExpr.isInstanceOf[ScBlock] || (commonParent.isInstanceOf[ScBlock] && occurrences.length == 1))
      while (prev != null && !checkEnd(prev, parExpr) && prev.isInstanceOf[ScExpression]) {
        parExpr = prev.asInstanceOf[ScExpression]
        prev = prev.getParent
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
      if (!(deleteOccurence && replaceAllOccurrences) && leaf.getParent != null && leaf.getParent.getParent.isInstanceOf[ScParenthesisedExpr]) {
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
          var createStmt: PsiElement = ScalaPsiElementFactory.createEnumerator(varName, ScalaRefactoringUtil.unparExpr(expression),
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
                case whSt: ScWhileStmt => whSt.expression.getOrElse(null)
                case doSt: ScDoStmt => doSt.getExprBody.getOrElse(null)
                case fE: ScFunctionExpr => fE.result.getOrElse(null)
                case forSt: ScForStatement => forSt.expression.getOrElse(null)
                case clause: ScCaseClause => clause.expr.getOrElse(null)
                case _ => null
              }
            }
          }
          if (needBraces && parExpr != null && parExpr.isValid) {
            parExpr = parExpr.replaceExpression(ScalaPsiElementFactory.createExpressionFromText("{" + parExpr.getText + "}", file.getManager), false)
          }
          val parent = if (needBraces && parExpr != null && parExpr.isValid) parExpr
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
          if (deleteOccurence && !replaceAllOccurrences) {
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
    editor.getSelectionModel.removeSelection
  }

  def invoke(project: Project, elements: Array[PsiElement], dataContext: DataContext) {
    //nothing to do
  }

  protected def getDialog(project: Project, editor: Editor, expr: ScExpression, typez: ScType, occurrences: Array[TextRange],
                          declareVariable: Boolean, validator: ScalaVariableValidator): ScalaIntroduceVariableDialog = {
    // Add occurences highlighting
    val highlighters = new java.util.ArrayList[RangeHighlighter]
    var highlightManager: HighlightManager = null
    if (editor != null) {
      highlightManager = HighlightManager.getInstance(project);
      val colorsManager = EditorColorsManager.getInstance
      val attributes = colorsManager.getGlobalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)
      if (occurrences.length > 1) {
        for (occurence <- occurrences)
          highlightManager.addRangeHighlight(editor, occurence.getStartOffset, occurence.getEndOffset, attributes, true, highlighters)
      }
    }

    val possibleNames = NameSuggester.suggestNames(expr, validator)
    val dialog = new ScalaIntroduceVariableDialog(project, typez, occurrences.length, validator, possibleNames)
    dialog.show
    if (!dialog.isOK()) {
      if (occurrences.length > 1) {
        WindowManager.getInstance.getStatusBar(project).setInfo(ScalaBundle.message("press.escape.to.remove.the.highlighting"))
      }
    } else {
      if (editor != null) {
        import collection.jcl.Conversions._

        for (highlighter <- highlighters) {
          highlightManager.removeSegmentHighlighter(editor, highlighter);
        }
      }
    }

    return dialog
  }

  def reportConflicts(conflicts: Array[String], project: Project): Boolean = {
    val conflictsDialog = new ConflictsDialog(project, conflicts: _*)
    conflictsDialog.show
    return conflictsDialog.isOK
  }

  /**
   * @throws IntroduceException
   */
  def showErrorMessage(text: String, project: Project): Nothing = {
    if (ApplicationManager.getApplication.isUnitTestMode) throw new RuntimeException(text)
    val dialog = new RefactoringMessageDialog("Introduce variable refactoring", text,
            HelpID.INTRODUCE_VARIABLE, "OptionPane.errorIcon", false, project)
    dialog.show
    throw new IntroduceException
  }

  private class IntroduceException extends Exception
}