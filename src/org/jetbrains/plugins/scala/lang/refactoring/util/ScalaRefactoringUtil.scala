package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import _root_.java.awt.Component
import _root_.javax.swing.event.{ListSelectionEvent, ListSelectionListener}
import java.util

import _root_.com.intellij.codeInsight.unwrap.ScopeHighlighter
import _root_.com.intellij.openapi.ui.popup.{JBPopupAdapter, JBPopupFactory, LightweightWindowEvent}
import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.{EditorColors, EditorColorsManager}
import com.intellij.openapi.editor.markup.{HighlighterLayer, HighlighterTargetArea, RangeHighlighter, TextAttributes}
import com.intellij.openapi.editor.{Editor, RangeMarker, VisualPosition}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.HelpID
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScLiteralPattern, ScPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScInterpolatedStringLiteral, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.util.JListCompatibility

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer


/**
 * User: Alexander Podkhalyuzin
 * Date: 23.06.2008
 */

object ScalaRefactoringUtil {
  def trimSpacesAndComments(editor: Editor, file: PsiFile, trimComments: Boolean = true) {
    var start = editor.getSelectionModel.getSelectionStart
    var end = editor.getSelectionModel.getSelectionEnd
    if (start == end) return
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
      case x: ScReferenceExpression =>
        x.resolve() match {
          case _: ScReferencePattern => return e
          case _ =>
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
        ScalaPsiElementFactory.createExpressionFromText(e.getText + " _", e.getManager)
      case _ => e
    }
  }

  def addPossibleTypes(scType: ScType, expr: ScExpression): Array[ScType] = {
    val types = new ArrayBuffer[ScType]
    if (scType != null) types += scType
    expr.getTypeWithoutImplicits(TypingContext.empty).foreach(types += _)
    expr.getTypeIgnoreBaseType(TypingContext.empty).foreach(types += _)
    if (types.isEmpty) types += psi.types.Any
    val unit = psi.types.Unit
    val result = if (types.contains(unit)) (types.distinct - unit) :+ unit else types.distinct
    result.toArray
  }

  def replaceSingletonTypes(scType: ScType): ScType = {
    def replaceSingleton(scType: ScType): (Boolean, ScType) = {
      ScType.extractDesignatorSingletonType(scType) match {
        case None => (false, scType)
        case Some(tp) => (true, tp)
      }
    }
    scType.recursiveUpdate(replaceSingleton)
  }

  def getExpression(project: Project, editor: Editor, file: PsiFile, startOffset: Int, endOffset: Int): Option[(ScExpression, Array[ScType])] = {
    val rangeText = file.getText.substring(startOffset, endOffset)

    def selectedInfixExpr(): Option[(ScExpression, Array[ScType])] = {
      val expr = ScalaPsiElementFactory.createOptionExpressionFromText(rangeText, file.getManager)
      expr match {
        case Some(expression: ScInfixExpr) =>
          val op1 = expression.operation
          if (ScalaRefactoringUtil.ensureFileWritable(project, file)) {
            var res: Option[(ScExpression, Array[ScType])] = None
            inWriteCommandAction(project) {
              val document = editor.getDocument
              document.insertString(endOffset, ")")
              document.insertString(startOffset, "(")
              val documentManager: PsiDocumentManager = PsiDocumentManager.getInstance(project)
              documentManager.commitDocument(document)
              val newOpt = getExpression(project, editor, file, startOffset, endOffset + 2)
              newOpt match {
                case Some((expression: ScExpression, typez)) =>
                  expression.getParent match {
                    case inf: ScInfixExpr =>
                      val op2 = inf.operation
                      import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils.priority
                      if (priority(op1.getText) == priority(op2.getText)) {
                        res = Some((expression.copy.asInstanceOf[ScExpression], typez))
                      }
                    case _ =>
                  }
                case None =>
              }
              document.deleteString(endOffset + 1, endOffset + 2)
              document.deleteString(startOffset, startOffset + 1)
              documentManager.commitDocument(document)
            }
            return res
          } else return None
        case _ => return None
      }
      None
    }

    def partOfStringLiteral(): Option[(ScExpression, Array[ScType])] = {
      val lit = PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, classOf[ScLiteral], false)
      val endLit = PsiTreeUtil.findElementOfClassAtOffset(file, endOffset, classOf[ScLiteral], false)
      if (lit == null || !lit.isString || lit != endLit) return None

      val prefix = lit match {
        case intrp: ScInterpolatedStringLiteral if rangeText.contains('$') => intrp.reference.fold("")(_.refName)
        case _ => ""
      }
      val quote = if (lit.isMultiLineString) "\"\"\"" else "\""

      val text = s"$prefix$quote$rangeText$quote"
      val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(text, lit.getContext, lit).asInstanceOf[ScLiteral]
      val tpe = expr.getNonValueType().getOrAny
      Some(expr, Array(tpe))
    }

    val element = PsiTreeUtil.findElementOfClassAtRange(file, startOffset, endOffset, classOf[ScExpression])

    if (element == null || element.getTextRange.getEndOffset != endOffset) {
      return selectedInfixExpr() orElse partOfStringLiteral()
    }

    val cachedType = element.getType(TypingContext.empty).getOrAny

    object ReferenceToFunction {
      def unapply(refExpr: ScReferenceExpression) = refExpr.bind() match {
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
    val types = addPossibleTypes(exprType, element).map(replaceSingletonTypes)
    Some((element, types))
  }

  def expressionToIntroduce(expr: ScExpression): ScExpression = {
    def copyExpr = expr.copy.asInstanceOf[ScExpression]
    def liftMethod = ScalaPsiElementFactory.createExpressionFromText(expr.getText + " _", expr.getManager)
    expr match {
      case ref: ScReferenceExpression =>
        ref.resolve() match {
          case fun: ScFunction if fun.paramClauses.clauses.length > 0 &&
                  fun.paramClauses.clauses.head.isImplicit => copyExpr
          case fun: ScFunction if !fun.parameters.isEmpty => liftMethod
          case meth: PsiMethod if !meth.getParameterList.getParameters.isEmpty => liftMethod
          case _ => copyExpr
        }
      case _ => copyExpr
    }
  }

  def ensureFileWritable(project: Project, file: PsiFile): Boolean = {
    val virtualFile = file.getVirtualFile
    val readonlyStatusHandler = ReadonlyStatusHandler.getInstance(project)
    val operationStatus = readonlyStatusHandler.ensureFilesWritable(virtualFile)
    !operationStatus.hasReadonlyFiles
  }

  def getOccurrenceRanges(element: PsiElement, enclosingContainer: PsiElement): Array[TextRange] = {
    def collectOccurrencesInLiteral(literal: ScLiteral, text: String, result: ArrayBuffer[TextRange]) {
      if (text.isEmpty) return

      val litStart = literal.getTextRange.getStartOffset
      val textToCheck = literal.getText
      var fromIdx = 0
      var indexOf = 0
      while (fromIdx < textToCheck.length && indexOf >= 0) {
        indexOf = textToCheck.indexOf(text, fromIdx)
        if (indexOf >= 0) {
          val start = litStart + indexOf
          val end = start + text.length
          fromIdx = end - litStart
          result += new TextRange(start, end)
        }
      }
    }
    def getTextOccurrenceInLiterals(text: String, enclosingContainer: PsiElement, filter: ScLiteral => Boolean): Array[TextRange] = {
      val result = ArrayBuffer[TextRange]()
      for (child <- enclosingContainer.getChildren) {
        child match {
          case toCheck: ScLiteral if PsiEquivalenceUtil.areElementsEquivalent(element, toCheck) =>
            result += toCheck.getTextRange
          case lit: ScLiteral if filter(lit) => collectOccurrencesInLiteral(lit, text, result)
          case _ => result ++= getTextOccurrenceInLiterals(text, child, filter)
        }
      }
      result.toArray
    }
    element match {
      case intrp: ScInterpolatedStringLiteral => 
        val prefix = intrp.reference.fold("")(_.refName)
        val fileText = intrp.getContainingFile.getText
        val text = fileText.substring(intrp.contentRange.getStartOffset, intrp.contentRange.getEndOffset)
        val refNameToResolved = mutable.HashMap[String, PsiElement]()
        intrp.depthFirst.foreach {
          case ref: ScReferenceExpression => refNameToResolved += ((ref.refName, ref.resolve()))
          case _ =>
        }
        val filter: ScLiteral => Boolean = {
          case toCheck: ScInterpolatedStringLiteral =>
            toCheck.reference.fold("")(_.refName) == prefix && toCheck.depthFirst.forall {
              case ref: ScReferenceExpression => refNameToResolved.get(ref.refName) == Some(ref.resolve())
              case _ => true
            }
          case _ => false
        }
        getTextOccurrenceInLiterals(text, enclosingContainer, filter)
      case lit: ScLiteral if lit.isString =>
        val text = lit.getValue.asInstanceOf[String]
        val filter: ScLiteral => Boolean = {
          case toCheck: ScInterpolatedStringLiteral if text.contains('$') => false
          case _ => true
        }
        getTextOccurrenceInLiterals(text, enclosingContainer, filter)
      case _ => getExprOccurrences(element, enclosingContainer).map(_.getTextRange)
    }
  }

  def getExprOccurrences(element: PsiElement, enclosingContainer: PsiElement): Array[ScExpression] = {
    val occurrences: ArrayBuffer[ScExpression] = new ArrayBuffer[ScExpression]()
    if (enclosingContainer == element) occurrences += enclosingContainer.asInstanceOf[ScExpression]
    else
      for (child <- enclosingContainer.getChildren) {
        if (PsiEquivalenceUtil.areElementsEquivalent(child, element)) {
          child match {
            case x: ScExpression =>
              x.getParent match {
                case y: ScMethodCall if y.args.exprs.size == 0 => occurrences += y
                case _ => occurrences += x
              }
            case _ =>
          }
        } else {
          occurrences ++= getExprOccurrences(element, child)
        }
      }
    occurrences.toArray
  }


  def unparExpr(expr: ScExpression): ScExpression = {
    expr match {
      case x: ScParenthesisedExpr =>
        x.expr match {
          case Some(e) => e
          case _ => x
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
    hasNlToken
  }

  def getCompatibleTypeNames(myType: ScType): util.HashMap[String, ScType] = {
    val map = new util.HashMap[String, ScType]
    map.put(ScType.presentableText(myType), myType)
    map
  }

  def getCompatibleTypeNames(myTypes: Array[ScType]): util.HashMap[String, ScType] = {
    val map = new util.HashMap[String, ScType]
    myTypes.foreach(myType => map.put(ScType.presentableText(myType), myType))
    map
  }

  def highlightOccurrences(project: Project, occurrences: Array[TextRange], editor: Editor) {
    val highlighters = new java.util.ArrayList[RangeHighlighter]
    var highlightManager: HighlightManager = null
    if (editor != null) {
      highlightManager = HighlightManager.getInstance(project)
      val colorsManager = EditorColorsManager.getInstance
      val attributes = colorsManager.getGlobalScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)
      for (occurence <- occurrences)
        highlightManager.addRangeHighlight(editor, occurence.getStartOffset, occurence.getEndOffset, attributes, true, highlighters)
    }
  }

  def highlightOccurrences(project: Project, occurrences: Array[PsiElement], editor: Editor) {
    highlightOccurrences(project, occurrences.map({
      el: PsiElement => el.getTextRange
    }), editor)
  }

  def showChooser[T <: PsiElement](editor: Editor, elements: Array[T], pass: PsiElement => Unit, title: String,
                                   elementName: T => String, highlightParent: Boolean = false) {
    class Selection {
      val selectionModel = editor.getSelectionModel
      val (start, end) = (selectionModel.getSelectionStart, selectionModel.getSelectionEnd)
      val scheme = editor.getColorsScheme
      val textAttributes = new TextAttributes
      textAttributes.setForegroundColor(scheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR))
      textAttributes.setBackgroundColor(scheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR))
      var selectionHighlighter: RangeHighlighter = null
      val markupModel = editor.getMarkupModel

      def addHighlighter() = if (selectionHighlighter == null) {
        selectionHighlighter = markupModel.addRangeHighlighter(start, end, HighlighterLayer.SELECTION + 1,
          textAttributes, HighlighterTargetArea.EXACT_RANGE)
      }

      def removeHighlighter() = if (selectionHighlighter != null) markupModel.removeHighlighter(selectionHighlighter)
    }

    val selection = new Selection
    val highlighter: ScopeHighlighter = new ScopeHighlighter(editor)
    val model = JListCompatibility.createDefaultListModel()
    for (element <- elements) {
      JListCompatibility.addElement(model, element)
    }
    val list = JListCompatibility.createJListFromModel(model)
    JListCompatibility.setCellRenderer(list, new DefaultListCellRendererAdapter {
      def getListCellRendererComponentAdapter(container: JListCompatibility.JListContainer,
                                              value: Object, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component = {
        val rendererComponent: Component = getSuperListCellRendererComponent(container.getList, value, index, isSelected, cellHasFocus)
        val element: T = value.asInstanceOf[T]
        if (element.isValid) {
          setText(elementName(element))
        }
        rendererComponent
      }
    })
    list.addListSelectionListener(new ListSelectionListener {
      def valueChanged(e: ListSelectionEvent) {
        highlighter.dropHighlight()
        val index: Int = list.getSelectedIndex
        if (index < 0) return
        val element: T = model.get(index).asInstanceOf[T]
        val toExtract: util.ArrayList[PsiElement] = new util.ArrayList[PsiElement]
        toExtract.add(if (highlightParent) element.getParent else element)
        highlighter.highlight(if (highlightParent) element.getParent else element, toExtract)
      }
    })

    JBPopupFactory.getInstance.createListPopupBuilder(list).setTitle(title).setMovable(false).setResizable(false).setRequestFocus(true).setItemChoosenCallback(new Runnable {
      def run() {
        pass(list.getSelectedValue.asInstanceOf[PsiElement])
      }
    }).addListener(new JBPopupAdapter {
      override def beforeShown(event: LightweightWindowEvent): Unit = {
        selection.addHighlighter()
      }
      override def onClosed(event: LightweightWindowEvent) {
        highlighter.dropHighlight()
        selection.removeHighlighter()
      }
    }).createPopup.showInBestPositionFor(editor)
  }

  def getShortText(expr: ScalaPsiElement): String = {
    val builder = new StringBuilder
    expr match {
      case ass: ScAssignStmt =>
        builder.append(getShortText(ass.getLExpression))
        builder.append(" = ")
        ass.getRExpression match {
          case Some(r) => builder.append(getShortText(r))
          case _ =>
        }
      case bl: ScBlock =>
        builder.append("{...}")
      case d: ScDoStmt =>
        builder.append("do {...} while (...)")
      case f: ScForStatement =>
        builder.append("for (...) ")
        if (f.isYield) builder.append("yield ")
        builder.append("{...}")
      case f: ScFunctionExpr =>
        val arrow = ScalaPsiUtil.functionArrow(f.getProject)
        builder.append(f.params.getText).append(s" $arrow {...}")
      case g: ScGenericCall =>
        builder.append(getShortText(g.referencedExpr))
        builder.append("[...]")
      case i: ScIfStmt =>
        builder.append("if (...) {...}")
        if (i.elseBranch != None) builder.append(" else {...}")
      case i: ScInfixExpr =>
        builder.append(getShortText(i.lOp))
        builder.append(" ")
        builder.append(getShortText(i.operation))
        builder.append(" ")
        builder.append(getShortText(i.rOp))
      case l: ScLiteral => builder.append(l.getText)
      case m: ScMatchStmt =>
        m.expr match {
          case Some(expression) => builder.append(getShortText(expression))
          case _ => builder.append("...")
        }
        builder.append(" match {...}")
      case m: ScMethodCall =>
        builder.append(getShortText(m.getInvokedExpr))
        if (m.argumentExpressions.length == 0) builder.append("()")
        else builder.append("(...)")
      case n: ScNewTemplateDefinition =>
        builder.append("new ")
        val types = n.extendsBlock.superTypes.filter {
          case ScDesignatorType(clazz: PsiClass) => clazz.qualifiedName != "scala.ScalaObject"
          case _ => true
        }
        for (tp <- types) {
          builder.append(ScType.presentableText(tp))
          if (tp != types(types.length - 1)) builder.append(" with ")
        }
        n.extendsBlock.templateBody match {
          case Some(tb) => builder.append(" {...}")
          case _ =>
        }
      case p: ScParenthesisedExpr =>
        builder.append("(")
        p.expr match {
          case Some(expression) => builder.append(getShortText(expression))
          case _ =>
        }
        builder.append(")")
      case p: ScPostfixExpr =>
        builder.append(getShortText(p.operand))
        builder.append(" ")
        builder.append(getShortText(p.operation))
      case p: ScPrefixExpr =>
        builder.append(getShortText(p.operation))
        builder.append(getShortText(p.operand))
      case r: ScReferenceExpression =>
        r.qualifier match {
          case Some(q) => builder.append(getShortText(q)).append(".")
          case _ =>
        }
        builder.append(r.refName)
      case r: ScReturnStmt =>
        builder.append("return ")
        r.expr match {
          case Some(expression) => builder.append(getShortText(expression))
          case _ =>
        }
      case s: ScSuperReference => builder.append(s.getText)
      case t: ScThisReference => builder.append(t.getText)
      case t: ScThrowStmt =>
        builder.append("throw ")
        t.body match {
          case Some(expression) => builder.append(getShortText(expression))
          case _ => builder.append("...")
        }
      case t: ScTryStmt =>
        builder.append("try {...}")
        if (t.catchBlock != None) builder.append(" catch {...}")
        if (t.finallyBlock != None) builder.append(" finally {...}")
      case t: ScTuple =>
        builder.append("(")
        val exprs = t.exprs
        for (expr <- exprs) {
          builder.append(getShortText(expr))
          if (expr != exprs.apply(exprs.length - 1)) builder.append(", ")
        }
        builder.append(")")
      case t: ScTypedStmt =>
        builder.append(getShortText(t.expr))
        builder.append(" : ")
        builder.append(t.typeElement match {
          case Some(te) => te.getText
          case _ => "..."
        })
      case u: ScUnderscoreSection =>
        if (u.bindingExpr == None) builder.append("_")
        else {
          builder.append(getShortText(u.bindingExpr.get))
          builder.append(" _")
        }
      case u: ScUnitExpr => builder.append("()")
      case w: ScWhileStmt => builder.append("while (...) {...}")
      case x: ScXmlExpr => builder.append(x.getText)
      case _ => builder.append(expr.getText)
    }
    builder.toString()
  }

  private[refactoring] def getLineText(editor: Editor): String = {
    val lineNumber = Option(editor.getSelectionModel.getSelectionEndPosition.getLine)
            .getOrElse(editor.getCaretModel.getLogicalPosition.line)
    if (lineNumber >= editor.getDocument.getLineCount) return ""
    val lineStart = editor.visualToLogicalPosition(new VisualPosition(lineNumber, 0))
    val nextLineStart = editor.visualToLogicalPosition(new VisualPosition(lineNumber + 1, 0))
    val start = editor.logicalPositionToOffset(lineStart)
    val end = editor.logicalPositionToOffset(nextLineStart)
    editor.getDocument.getText.substring(start, end)
  }

  def afterExpressionChoosing(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext,
                        refactoringName: String, exprFilter: (ScExpression) => Boolean = e => true)(invokesNext: => Unit) {

    if (!editor.getSelectionModel.hasSelection) {
      val offset = editor.getCaretModel.getOffset
      val element: PsiElement = file.findElementAt(offset) match {
        case w: PsiWhiteSpace if w.getTextRange.getStartOffset == offset &&
                w.getText.contains("\n") => file.findElementAt(offset - 1)
        case p => p
      }
      def getExpressions: Array[ScExpression] = {
        val res = new ArrayBuffer[ScExpression]
        var parent = element
        while (parent != null && !parent.getText.contains("\n")) {
          parent match {
            case expr: ScExpression => res += expr
            case _ =>
          }
          parent = parent.getParent
        }
        res.toArray
      }
      val expressions = getExpressions.filter(exprFilter)
      def chooseExpression(expr: ScExpression) {
        editor.getSelectionModel.setSelection(expr.getTextRange.getStartOffset, expr.getTextRange.getEndOffset)
        invokesNext
      }
      if (expressions.length == 0)
        editor.getSelectionModel.selectLineAtCaret()
      else if (expressions.length == 1) {
        chooseExpression(expressions(0))
        return
      } else {
        showChooser(editor, expressions, elem =>
          chooseExpression(elem.asInstanceOf[ScExpression]), ScalaBundle.message("choose.expression.for", refactoringName), (expr: ScExpression) => {
          getShortText(expr)
        })
        return
      }
    }
    invokesNext
  }

  def fileEncloser(startOffset: Int, file: PsiFile): PsiElement = {
    if (file.asInstanceOf[ScalaFile].isScriptFile()) file
    else {
      val elem = file.findElementAt(startOffset)
      val result = ScalaPsiUtil.getParentOfType(elem, classOf[ScExtendsBlock], classOf[PsiFile])
      if (result == null) {
        for (child <- file.getChildren) {
          val textRange: TextRange = child.getTextRange
          if (textRange.contains(startOffset)) return child
        }
      }
      result
    }
  }

  def isInplaceAvailable(editor: Editor): Boolean =
    editor.getSettings.isVariableInplaceRenameEnabled && !ApplicationManager.getApplication.isUnitTestMode

  def enclosingContainer(file: PsiFile, textRanges: TextRange*): PsiElement = {
    Option(commonParent(file, textRanges: _*))
            .map(elem => elem.firstChild.getOrElse(elem)) //to make enclosing container non-strict
            .flatMap(_.scopes.toStream.headOption).orNull
  }

  def commonParent(file: PsiFile, textRanges: TextRange*): PsiElement = {
    val elemSeq = (for (occurence <- textRanges) yield file.findElementAt(occurence.getStartOffset)).toSeq ++
            (for (occurence <- textRanges) yield file.findElementAt(occurence.getEndOffset - 1)).toSeq
    PsiTreeUtil.findCommonParent(elemSeq: _*)
  }

  def isLiteralPattern(file: PsiFile, textRange: TextRange): Boolean = {
    val parent = ScalaRefactoringUtil.commonParent(file, textRange)
    val literalPattern = PsiTreeUtil.getParentOfType(parent, classOf[ScLiteralPattern])
    literalPattern != null && literalPattern.getTextRange == textRange
  }

  /**
   * @throws IntroduceException
   */
  def showErrorMessage(text: String, project: Project, editor: Editor, refactoringName: String): Nothing = {
    if (ApplicationManager.getApplication.isUnitTestMode) throw new RuntimeException(text)
    CommonRefactoringUtil.showErrorHint(project, editor, text, refactoringName, HelpID.INTRODUCE_PARAMETER)
    throw new IntroduceException
  }

  def checkFile(file: PsiFile, project: Project, editor: Editor, refactoringName: String) {
    if (!file.isInstanceOf[ScalaFile])
      showErrorMessage(ScalaBundle.message("only.for.scala"), project, editor, refactoringName)

    if (!ScalaRefactoringUtil.ensureFileWritable(project, file))
      showErrorMessage(ScalaBundle.message("file.is.not.writable"), project, editor, refactoringName)
  }

  def checkCanBeIntroduced(expr: ScExpression, action: (String) => Unit = s => {}): Boolean = {
    var errorMessage: String = null
    ScalaPsiUtil.getParentOfType(expr, classOf[ScConstrBlock]) match {
      case block: ScConstrBlock =>
        for {
          selfInv <- block.selfInvocation
          args <- selfInv.args
          if args.isAncestorOf(expr)
        } errorMessage = ScalaBundle.message("cannot.refactor.arg.in.self.invocation.of.constructor")
      case _ =>
    }

    val guard: ScGuard = PsiTreeUtil.getParentOfType(expr, classOf[ScGuard])
    if (guard != null && guard.getParent.isInstanceOf[ScCaseClause])
      errorMessage = ScalaBundle.message("refactoring.is.not.supported.in.guard")

    expr match {
      case block: ScBlock if !block.hasRBrace && block.statements.size != 1 =>
        errorMessage = ScalaBundle.message("cannot.refactor.not.expression")
      case _ =>
    }

    if (errorMessage == null) errorMessage = expr.getParent match {
      case inf: ScInfixExpr if inf.operation == expr => ScalaBundle.message("cannot.refactor.not.expression")
      case post: ScPostfixExpr if post.operation == expr => ScalaBundle.message("cannot.refactor.not.expression")
      case _: ScGenericCall => ScalaBundle.message("cannot.refactor.under.generic.call")
      case _ if expr.isInstanceOf[ScConstrExpr] => ScalaBundle.message("cannot.refactor.constr.expression")
      case _: ScArgumentExprList if expr.isInstanceOf[ScAssignStmt] => ScalaBundle.message("cannot.refactor.named.arg")
      case _: ScLiteralPattern => ScalaBundle.message("cannot.refactor.literal.pattern")
      case par: ScClassParameter =>
        par.containingClass match {
          case clazz: ScClass if clazz.isTopLevel => ScalaBundle.message("cannot.refactor.class.parameter.top.level")
          case _ => null
        }
      case _ => null
    }
    if (errorMessage != null) action(errorMessage)
    errorMessage == null
  }

  def replaceOccurence(textRange: TextRange, newString: String, file: PsiFile, editor: Editor): RangeMarker = {
    val document = editor.getDocument
    val documentManager = PsiDocumentManager.getInstance(editor.getProject)
    var shift = 0
    val start = textRange.getStartOffset
    document.replaceString(start, textRange.getEndOffset, newString)
    val newRange = new TextRange(start, start + newString.length)
    documentManager.commitDocument(document)
    val leaf = file.findElementAt(start)
    val parent = leaf.getParent
    parent match {
      case null =>
      case ChildOf(pars @ ScParenthesisedExpr(inner)) if !ScalaPsiUtil.needParentheses(pars, inner) =>
        val textRange = pars.getTextRange
        val afterWord = textRange.getStartOffset > 0 && {
          val prevElemType = file.findElementAt(textRange.getStartOffset - 1).getNode.getElementType
          ScalaTokenTypes.IDENTIFIER_TOKEN_SET.contains(prevElemType) || ScalaTokenTypes.KEYWORDS.contains(prevElemType)
        }
        shift = pars.getTextRange.getStartOffset - inner.getTextRange.getStartOffset + (if (afterWord) 1 else 0)
        document.replaceString(textRange.getStartOffset, textRange.getEndOffset, (if (afterWord) " " else "") + newString)
      case ChildOf(ScPostfixExpr(_, `parent`))=>
        //This case for block argument expression
        val textRange = parent.getTextRange
        document.replaceString(textRange.getStartOffset, textRange.getEndOffset, "(" + newString + ")")
        shift = 1
      case _: ScReferencePattern =>
        val textRange = parent.getTextRange
        document.replaceString(textRange.getStartOffset, textRange.getEndOffset, "`" + newString + "`")
      case lit: ScLiteral =>
        val prefix = lit match {
          case intrp: ScInterpolatedStringLiteral => intrp.reference.fold("")(_.refName)
          case _ => ""
        }
        val replaceAsInjection = Seq("s", "raw").contains(prefix)

        if (replaceAsInjection) {
          val withNextChar = file.getText.substring(newRange.getStartOffset, newRange.getEndOffset + 1)
          val needBraces = ScalaNamesUtil.isIdentifier(withNextChar) && withNextChar.last != '$'
          val text = if (needBraces) s"$${$newString}" else s"$$$newString"
          shift += (if (needBraces) 2 else 1)
          document.replaceString(newRange.getStartOffset, newRange.getEndOffset, text)
        } else {
          val quote = if (lit.isMultiLineString) "\"\"\"" else "\""
          val isStart = newRange.getStartOffset == lit.contentRange.getStartOffset
          val isEnd = newRange.getEndOffset == lit.contentRange.getEndOffset
          val firstPart = if (!isStart) s"$quote + " else ""
          val lastPart = if (!isEnd) s" + $prefix$quote" else ""
          val text = s"$firstPart$newString$lastPart"
          val literalRange = lit.getTextRange
          val startOffset = if (isStart) literalRange.getStartOffset else newRange.getStartOffset
          val endOffset = if (isEnd) literalRange.getEndOffset else newRange.getEndOffset
          document.replaceString(startOffset, endOffset, text)
          shift = if (isStart) startOffset - newRange.getStartOffset else firstPart.length
        }
      case _ =>
    }
    documentManager.commitDocument(document)
    val newStart = start + shift
    val newEnd = newStart + newString.length
    val newExpr = PsiTreeUtil.findElementOfClassAtRange(file, newStart, newEnd, classOf[ScExpression])
    val newPattern = PsiTreeUtil.findElementOfClassAtOffset(file, newStart, classOf[ScPattern], true)
    Option(newExpr).orElse(Option(newPattern))
            .map(elem => document.createRangeMarker(elem.getTextRange))
            .getOrElse(throw new IntroduceException)
  }


  def replaceOccurences(occurences: Array[TextRange], newString: String, file: PsiFile, editor: Editor): Array[TextRange] = {
    val revercedRangeMarkers = occurences.reverseMap(replaceOccurence(_, newString, file, editor))
    revercedRangeMarkers.reverseMap(rm => new TextRange(rm.getStartOffset, rm.getEndOffset))
  }

  def statementsAndMembersInClass(aClass: ScTemplateDefinition): Seq[PsiElement] = {
    val extendsBlock = aClass.extendsBlock
    if (extendsBlock == null) return Nil
    val body = extendsBlock.templateBody
    val earlyDefs = extendsBlock.earlyDefinitions
    (earlyDefs ++ body)
            .flatMap(_.children)
            .filter(child => child.isInstanceOf[ScBlockStatement] || child.isInstanceOf[ScMember])
            .toSeq
  }

  @tailrec
  def findParentExpr(elem: PsiElement): ScExpression = {
    def checkEnd(nextParent: PsiElement, parExpr: ScExpression): Boolean = {
      if (parExpr.isInstanceOf[ScBlock]) return true
      val result: Boolean = nextParent match {
        case _: ScBlock => true
        case forSt: ScForStatement if forSt.body.orNull == parExpr => false //in this case needBraces == true
        case forSt: ScForStatement => true
        case _ => false
      }
      result || needBraces(parExpr, nextParent)
    }
    val interpolated = Option(PsiTreeUtil.getParentOfType(elem, classOf[ScInterpolatedStringLiteral], false))
    val expr = interpolated getOrElse PsiTreeUtil.getParentOfType(elem, classOf[ScExpression], false)
    val nextPar = nextParent(expr, elem.getContainingFile)
    nextPar match {
      case prevExpr: ScExpression if !checkEnd(nextPar, expr) => findParentExpr(prevExpr)
      case prevExpr: ScExpression if checkEnd(nextPar, expr) => expr
      case _ => expr
    }
  }

  def nextParent(expr: ScExpression, file: PsiFile): PsiElement = {
    if (expr == null) file
    else expr.getParent match {
      case args: ScArgumentExprList => args.getParent
      case other => other
    }
  }

  def needBraces(parExpr: PsiElement, prev: PsiElement): Boolean = {
    prev match {
      case tb: ScTryBlock if !tb.hasRBrace => true
      case _: ScBlock | _: ScTemplateBody | _: ScEarlyDefinitions | _: ScalaFile | _: ScCaseClause => false
      case _: ScFunction => true
      case Both(fun: ScFunction, _ childOf (_: ScTemplateBody | _: ScEarlyDefinitions)) => true
      case ifSt: ScIfStmt if Seq(ifSt.thenBranch, ifSt.elseBranch) contains Option(parExpr) => true
      case forSt: ScForStatement if forSt.body.orNull == parExpr => true
      case forSt: ScForStatement => false
      case _: ScEnumerator | _: ScGenerator => false
      case guard: ScGuard if guard.getParent.isInstanceOf[ScEnumerators] => false
      case whSt: ScWhileStmt if whSt.body.orNull == parExpr => true
      case doSt: ScDoStmt if doSt.getExprBody.orNull == parExpr => true
      case finBl: ScFinallyBlock if finBl.expression.orNull == parExpr => true
      case fE: ScFunctionExpr =>
        fE.getContext match {
          case be: ScBlock if be.lastExpr == Some(fE) => false
          case _ => true
        }
      case _ => false
    }
  }


  def checkForwardReferences(expr: ScExpression, position: PsiElement): Boolean = {
    var result = true
    val visitor = new ScalaRecursiveElementVisitor() {
      override def visitReferenceExpression(ref: ScReferenceExpression) {
        ref.getParent match {
          case ScInfixExpr(_, `ref`, _) =>
          case ScPostfixExpr(_, `ref`) =>
          case ScPrefixExpr(`ref`, _) =>
          case _ =>
            val newRef = ScalaPsiElementFactory.createExpressionFromText(ref.getText, position)
                    .asInstanceOf[ScReferenceExpression]
            result &= ref.resolve() == newRef.resolve()
        }
        super.visitReferenceExpression(ref)
      }
    }
    expr.accept(visitor)
    result
  }

  def container(element: PsiElement, file: PsiFile): PsiElement = {
    def oneExprBody(fun: ScFunctionDefinition): Boolean = fun.body match {
      case Some(_: ScBlock) => false
      case Some(newTd: ScNewTemplateDefinition) => false
      case Some(_) => true
      case None => false
    }

    if (element == null) file
    else {
      val candidate = ScalaPsiUtil.getParentOfType(element, false, classOf[ScalaFile], classOf[ScBlock],
        classOf[ScTemplateBody], classOf[ScCaseClause], classOf[ScEarlyDefinitions])

      val funDef = PsiTreeUtil.getParentOfType(element, classOf[ScFunctionDefinition])

      val isAnonFunBlock = candidate match {
        case b: ScBlock if b.isAnonymousFunction => true
        case _ => false
      }

      if (funDef != null && PsiTreeUtil.isAncestor(candidate, funDef, true) && oneExprBody(funDef))
        funDef.body.get
      else if (isAnonFunBlock) container(candidate.getContext, file)
      else candidate
    }
  }

  def inSuperConstructor(element: PsiElement, aClass: ScTemplateDefinition): Boolean = {
    aClass.extendsBlock.templateParents match {
      case Some(parents) if parents.isAncestorOf(element) => true
      case None => false
    }
  }

  def selectedElements(editor: Editor, file: ScalaFile, trimComments: Boolean): Seq[PsiElement] = {
    trimSpacesAndComments(editor, file, trimComments = trimComments)
    val startElement: PsiElement = file.findElementAt(editor.getSelectionModel.getSelectionStart)
    val endElement: PsiElement = file.findElementAt(editor.getSelectionModel.getSelectionEnd - 1)
    val elements = ScalaPsiUtil.getElementsRange(startElement, endElement) match {
      case Seq(b: ScBlock) if !b.hasRBrace => b.children.toSeq
      case elems => elems
    }
    elements
  }

  @tailrec
  def findEnclosingBlockStatement(place: PsiElement): Option[ScBlockStatement] = {
    place match {
      case null => None
      case (bs: ScBlockStatement) childOf (_: ScBlock | _: ScEarlyDefinitions | _: ScalaFile | _: ScTemplateBody) =>
        Some(bs)
      case other => findEnclosingBlockStatement(other.getParent)
    }
  }

  private[refactoring] case class RevertInfo(fileText: String, caretOffset: Int)

  private[refactoring] class IntroduceException extends Exception

}