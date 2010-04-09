package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import _root_.com.intellij.codeInsight.unwrap.ScopeHighlighter
import _root_.com.intellij.openapi.ui.popup.{LightweightWindowEvent, JBPopupAdapter, JBPopupFactory}
import _root_.java.util.{ArrayList, HashMap, Comparator}
import _root_.javax.swing.event.{ListSelectionEvent, ListSelectionListener}
import _root_.java.awt.Component
import _root_.javax.swing.{DefaultListCellRenderer, DefaultListModel, JList}
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorColors}

import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import psi.api.base.patterns.{ScCaseClause, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import psi.api.expr._
import psi.impl.ScalaPsiElementFactory
import com.intellij.codeInsight.PsiEquivalenceUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import collection.mutable.ArrayBuffer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.util.ScalaUtils
import psi.types._
import psi.api.statements.{ScFunction, ScFunctionDefinition}
import lang.resolve.ScalaResolveResult
import psi.api.expr.xml.ScXmlExpr
import psi.ScalaPsiElement
import psi.api.base.ScLiteral

/**
 * User: Alexander Podkhalyuzin
 * Date: 23.06.2008
 */

object ScalaRefactoringUtil {      
  def trimSpacesAndComments(editor: Editor, file: PsiFile, trimComments: Boolean = true) {
    var start = editor.getSelectionModel.getSelectionStart
    var end = editor.getSelectionModel.getSelectionEnd
    while (file.findElementAt(start).isInstanceOf[PsiWhiteSpace] ||
            (file.findElementAt(start).isInstanceOf[PsiComment] && trimComments) ||
            file.getText.charAt(start) == '\n' ||
            file.getText.charAt(start) == ' ') start = start + 1
    while (file.findElementAt(end - 1).isInstanceOf[PsiWhiteSpace] ||
            (file.findElementAt(end - 1).isInstanceOf[PsiComment] && trimComments) ||
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
    val cachedType = element.getType(TypingContext.empty).getOrElse(Any)

    object ReferenceToFunction {
      def unapply(refExpr: ScReferenceExpression) = refExpr.bind match {
        case Some(srr: ScalaResolveResult) if srr.element.isInstanceOf[ScFunction] => Some(srr.element.asInstanceOf[ScFunction])
        case _ => None
      }
    }
    // Handle omitted parentheses in calls to functions with empty parameter list.
    // todo add a test for case with only implicit parameter list.
    val exprType = (element, cachedType) match {
      case (ReferenceToFunction(func), ScFunctionType(returnType, _)) if (func: ScFunction).parameters.isEmpty => returnType
      case _ => cachedType
    }
    return Some((element, exprType))
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

  def showChooser[T <: PsiElement](editor: Editor, elements: Array[T], pass: PsiElement => Unit, title: String,
                                   elementName: T => String): Unit = {
    val highlighter: ScopeHighlighter = new ScopeHighlighter(editor)
    val model: DefaultListModel = new DefaultListModel
    for (element <- elements) {
      model.addElement(element)
    }
    val list: JList = new JList(model)
    list.setCellRenderer(new DefaultListCellRenderer {
      override def getListCellRendererComponent(list: JList, value: Object, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component = {
        val rendererComponent: Component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        val buf: StringBuffer = new StringBuffer
        val element: T = value.asInstanceOf[T]
        if (element.isValid) {
          setText(elementName(element))
        }
        return rendererComponent
      }
    })
    list.addListSelectionListener(new ListSelectionListener {
      def valueChanged(e: ListSelectionEvent): Unit = {
        highlighter.dropHighlight
        val index: Int = list.getSelectedIndex
        if (index < 0) return
        val element: T = model.get(index).asInstanceOf[T]
        val toExtract: ArrayList[PsiElement] = new ArrayList[PsiElement]
        toExtract.add(element)
        highlighter.highlight(element, toExtract)
      }
    })
    JBPopupFactory.getInstance.createListPopupBuilder(list).setTitle(title).setMovable(false).setResizable(false).setRequestFocus(true).setItemChoosenCallback(new Runnable {
      def run: Unit = {
        pass(list.getSelectedValue.asInstanceOf[T])
      }
    }).addListener(new JBPopupAdapter {
      override def onClosed(event: LightweightWindowEvent): Unit = {
        highlighter.dropHighlight
      }
    }).createPopup.showInBestPositionFor(editor)
  }
  
  def getShortText(expr: ScalaPsiElement): String = {
    val builder = new StringBuilder
    expr match {
      case ass: ScAssignStmt => {
        builder.append(getShortText(ass.getLExpression))
        builder.append(" = ")
        ass.getRExpression match {
          case Some(r) => builder.append(getShortText(r))
          case _ =>
        }
      }
      case bl: ScBlock => {
        builder.append("{...}")
      }
      case d: ScDoStmt => {
        builder.append("do {...} while (...)")
      }
      case f: ScForStatement => {
        builder.append("for (...) ")
        if (f.isYield) builder.append("yield ")
        builder.append("{...}")
      }
      case f: ScFunctionExpr => {
        builder.append(f.params.getText).append(" => {...}")
      }
      case g: ScGenericCall => {
        builder.append(getShortText(g.referencedExpr))
        builder.append("[...]")
      }
      case i: ScIfStmt => {
        builder.append("if (...) {...}")
        if (i.elseBranch != None) builder.append(" else {...}")
      }
      case i: ScInfixExpr => {
        builder.append(getShortText(i.lOp))
        builder.append(" ")
        builder.append(getShortText(i.operation))
        builder.append(" ")
        builder.append(getShortText(i.rOp))
      }
      case l: ScLiteral => builder.append(l.getText)
      case m: ScMatchStmt => {
        m.expr match {
          case Some(expr) => builder.append(getShortText(expr))
          case _ => builder.append("...")
        }
        builder.append(" match {...}")
      }
      case m: ScMethodCall => {
        builder.append(getShortText(m.getInvokedExpr))
        if (m.argumentExpressions.length == 0) builder.append("()")
        else builder.append("(...)")
      }
      case n: ScNewTemplateDefinition => {
        builder.append("new ")
        val types = n.extendsBlock.superTypes
        for (tp <- types) {
          builder.append(ScType.presentableText(tp))
          if (tp != types(types.length - 1)) builder.append(" with ")
        }
        n.extendsBlock.templateBody match {
          case Some(tb) => builder.append(" {...}")
          case _ =>
        }
      }
      case p: ScParenthesisedExpr => {
        builder.append("(")
        p.expr match {case Some(expr) => builder.append(getShortText(expr)) case _ =>}
        builder.append(")")
      }
      case p: ScPostfixExpr => {
        builder.append(getShortText(p.operand))
        builder.append(" ")
        builder.append(getShortText(p.operation))
      }
      case p: ScPrefixExpr => {
        builder.append(getShortText(p.operation))
        builder.append(getShortText(p.operand))
      }
      case r: ScReferenceExpression => {
        r.qualifier match {
          case Some(q) => builder.append(getShortText(q)).append(".")
          case _ =>
        }
        builder.append(r.refName)
      }
      case r: ScReturnStmt => {
        builder.append("return ")
        r.expr match {
          case Some(expr) => builder.append(getShortText(expr))
          case _ =>
        }
      }
      case s: ScSuperReference => builder.append(s.getText)
      case t: ScThisReference => builder.append(t.getText)
      case t: ScThrowStmt => {
        builder.append("throw ")
        t.body match {
          case Some(expr) => builder.append(getShortText(expr))
          case _ => builder.append("...")
        }
      }
      case t: ScTryStmt => {
        builder.append("try {...}")
        if (t.catchBlock != None) builder.append(" catch {...}")
        if (t.finallyBlock != None) builder.append(" finally {...}")
      }
      case t: ScTuple => {
        builder.append("(")
        val exprs = t.exprs
        for (expr <- exprs) {
          builder.append(getShortText(expr))
          if (expr != exprs.apply(exprs.length - 1)) builder.append(", ")
        }
        builder.append(")")
      }
      case t: ScTypedStmt => {
        builder.append(getShortText(t.expr))
        builder.append(" : ")
        builder.append(t.typeElement match {case Some(te) => te.getText case _ => "..."})
      }
      case u: ScUnderscoreSection => {
        if (u.bindingExpr == None) builder.append("_")
        else {
          builder.append(getShortText(u.bindingExpr.get))
          builder.append(" _")
        }
      }
      case u: ScUnitExpr => builder.append("()")
      case w: ScWhileStmt => builder.append("while (...) {...}")
      case x: ScXmlExpr => builder.append(x.getText)
      case _ => builder.append(expr.getText)
    }
    builder.toString
  }
}