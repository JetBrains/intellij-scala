package org.jetbrains.plugins.scala.lang.refactoring.util

import _root_.org.jetbrains.plugins.scala.lang.psi.types.ScType
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorColors}

import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import java.util.{HashMap, Comparator}
import parser.ScalaElementTypes
import psi.api.base.patterns.{ScCaseClause, ScReferencePattern}
import psi.api.expr._
import psi.api.ScalaFile
import psi.api.statements.ScVariable
import psi.api.statements.ScValue
import psi.api.toplevel.typedef.ScMember
import psi.api.base.ScStableCodeReferenceElement
import psi.impl.ScalaPsiElementFactory
import psi.api.statements.ScFunctionDefinition
import com.intellij.codeInsight.PsiEquivalenceUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.util.ReflectionCache
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.lexer._
import scala.util.ScalaUtils
/**
 * User: Alexander Podkhalyuzin
 * Date: 23.06.2008
 */

object ScalaRefactoringUtil {      
  def trimSpacesAndComments(editor: Editor, file: PsiFile) {
    var start = editor.getSelectionModel.getSelectionStart
    var end = editor.getSelectionModel.getSelectionEnd
    while (file.findElementAt(start).isInstanceOf[PsiWhiteSpace] ||
            file.findElementAt(start).isInstanceOf[PsiComment] ||
            file.getText.charAt(start) == '\n' ||
            file.getText.charAt(start) == ' ') start = start + 1
    while (file.findElementAt(end - 1).isInstanceOf[PsiWhiteSpace] ||
            file.findElementAt(end - 1).isInstanceOf[PsiComment] ||
           file.getText.charAt(end - 1) == '\n' ||
           file.getText.charAt(end - 1) == ' ') end = end - 1
    editor.getSelectionModel.setSelection(start, end)
  }

  def getExprFrom(expr: ScExpression): ScExpression = {
    var e = unparExpr(expr)
    e match {
      case x: ScReferenceExpression => {
        x.resolve match {
          case _: ScReferencePattern => return e
          case _ =>
        }
      }
      case _ =>
    }
    var hasNlToken = false
    val text = e.getText
    var i = text.length - 1
    while (i >= 0 && (text(i) == ' ' || text(i) == '\n')) {
      if (text(i) == '\n') hasNlToken = true
      i = i - 1
    }
    if (hasNlToken) e = ScalaPsiElementFactory.createExpressionFromText(text.substring(0, i + 1), e.getManager)
    e.getParent match {
      case x: ScMethodCall if x.args.exprs.size > 0 =>
        return ScalaPsiElementFactory.createExpressionFromText(e.getText + " _", e.getManager)
      case _ => return e
    }
  }

  def getExpression(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int): Option[(ScExpression, ScType)] = {
    val element = PsiTreeUtil.findElementOfClassAtRange(file, startOffset, endOffset, classOf[ScExpression])
    if (element == null || element.getTextRange.getStartOffset != startOffset || element.getTextRange.getEndOffset != endOffset) {
      val rangeText = file.getText.substring(startOffset, endOffset)
      val expr = ScalaPsiElementFactory.createOptionExpressionFromText(rangeText, file.getManager)
      expr match {
        case Some(expression: ScInfixExpr) => {
          val op1 = expression.operation
          if (ScalaRefactoringUtil.ensureFileWritable(project, file)) {
            var res: Option[(ScExpression, ScType)] = None
            ScalaUtils.runWriteAction(new Runnable {
              def run: Unit = {
                val document = editor.getDocument
                document.insertString(endOffset, ")")
                document.insertString(startOffset, "(")
                val documentManager: PsiDocumentManager = PsiDocumentManager.getInstance(project)
                documentManager.commitDocument(document)
                val newOpt = getExpression(project, editor, file, startOffset, endOffset + 2)
                newOpt match {
                  case Some((expression: ScExpression, typez)) => {
                    expression.getParent match {
                      case inf: ScInfixExpr => {
                        val op2 = inf.operation
                        import parser.util.ParserUtils.priority
                        if (priority(op1.getText) == priority(op2.getText)) {
                          res = Some((expression.copy.asInstanceOf[ScExpression], typez))
                        }
                      }
                      case _ =>
                    }
                  }
                  case None =>
                }
                document.deleteString(endOffset + 1, endOffset + 2)
                document.deleteString(startOffset, startOffset + 1)
                documentManager.commitDocument(document)
              }
            }, project, "IntroduceVariable helping writer")
            return res
          } else return None
        }
        case _ => return None
      }
      return None
    }
    return Some((element, element.cashedType))
  }

  def getEnclosingContainer(file: PsiFile, startOffset: Int, endOffset: Int): PsiElement = {
    val common = PsiTreeUtil.findCommonParent(file.findElementAt(startOffset), file.findElementAt(endOffset))
    getEnclosingContainer(common)
  }

  //todo: rewrite tests and make it private
  def getEnclosingContainer(element: PsiElement): PsiElement = {
    def get(parent: PsiElement): PsiElement = {
      parent match {
        case null =>
        case x: ScBlock if x != element =>
        //todo: case _: ScEnumerators =>
        case _: ScExpression => parent.getParent match {
          case _: ScForStatement | _: ScCaseClause |
               _: ScFinallyBlock | _: ScFunctionDefinition =>
          case x => return get(x)
        }
        case _ => return get(parent.getParent)
      }
      return parent
    }
    return get(element)
  }

  def ensureFileWritable(project: Project, file: PsiFile): Boolean = {
    val virtualFile = file.getVirtualFile()
    val readonlyStatusHandler = ReadonlyStatusHandler.getInstance(project)
    val operationStatus = readonlyStatusHandler.ensureFilesWritable(virtualFile)
    return !operationStatus.hasReadonlyFiles()
  }
  def getOccurrences(expr: ScExpression, enclosingContainer: PsiElement): Array[TextRange] = {
    val occurrences: ArrayBuffer[TextRange] = new ArrayBuffer[TextRange]()
    if (enclosingContainer == expr) occurrences += enclosingContainer.asInstanceOf[ScExpression].getTextRange
    else
      for (child <- enclosingContainer.getChildren) {
        if (PsiEquivalenceUtil.areElementsEquivalent(child, expr, comparator, false)) {
          child match {
            case x: ScExpression => {
              x.getParent match {
                case y: ScMethodCall if y.args.exprs.size == 0 => occurrences += y.getTextRange
                case _ => occurrences += x.getTextRange
              }
            }
            case _ =>
          }
        } else {
          occurrences ++= getOccurrences(expr, child)
        }
      }
    return occurrences.toArray
  }

  def unparExpr(expr: ScExpression): ScExpression = {
    expr match {
      case x: ScParenthesisedExpr => {
        x.expr match {
          case Some(e) => e
          case _ => x
        }
      }
      case _ => expr
    }
  }

  def hasNltoken(e: PsiElement): Boolean = {
    var hasNlToken = false
    val text = e.getText
    var i = text.length - 1
    while (i >= 0 && (text(i) == ' ' || text(i) == '\n')) {
      if (text(i) == '\n') hasNlToken = true
      i = i - 1
    }
    return hasNlToken
  }

  def getCompatibleTypeNames(myType: ScType): HashMap[String, ScType] = {
    val map = new HashMap[String, ScType]
    map.put(ScType.presentableText(myType), myType)
    return map
  }

  private val comparator = new Comparator[PsiElement]() {
    def compare(element1: PsiElement, element2: PsiElement): Int = {
      if (element1 == element2) return 0
      if (element1.isInstanceOf[ScParameter] && element2.isInstanceOf[ScParameter]) {
        val name1 = element1.asInstanceOf[ScParameter].getName
        val name2 = element2.asInstanceOf[ScParameter].getName
        if (name1 != null && name2 != null) {
          return name1.compareTo(name2)
        }
      }
      return 1
    }
  }

  def highlightOccurrences(project: Project, occurrences: Array[TextRange], editor: Editor): Unit = {
    val highlighters = new java.util.ArrayList[RangeHighlighter]
    var highlightManager: HighlightManager = null
    if (editor != null) {
      highlightManager = HighlightManager.getInstance(project);
      val colorsManager = EditorColorsManager.getInstance
      val attributes = colorsManager.getGlobalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)
      for (occurence <- occurrences)
        highlightManager.addRangeHighlight(editor, occurence.getStartOffset, occurence.getEndOffset, attributes, true, highlighters)
    }
  }

  def highlightOccurrences(project: Project, occurrences: Array[PsiElement], editor: Editor): Unit = {
    highlightOccurrences(project, occurrences.map({el: PsiElement => el.getTextRange}), editor)
  }
}