package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import _root_.com.intellij.codeInsight.unwrap.ScopeHighlighter
import _root_.com.intellij.openapi.ui.popup.{LightweightWindowEvent, JBPopupAdapter, JBPopupFactory}
import _root_.java.util.Comparator
import _root_.javax.swing.event.{ListSelectionEvent, ListSelectionListener}
import _root_.java.awt.Component
import _root_.javax.swing.{DefaultListCellRenderer, DefaultListModel, JList}
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.openapi.editor.colors.{EditorColorsManager, EditorColors}

import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScLiteralPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import psi.api.expr._
import psi.impl.ScalaPsiElementFactory
import com.intellij.codeInsight.PsiEquivalenceUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import scala.collection.mutable.ArrayBuffer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.util.ScalaUtils
import psi.types._
import psi.api.statements.ScFunction
import lang.resolve.ScalaResolveResult
import psi.api.expr.xml.ScXmlExpr
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiUtil, ScImportsHolder, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScLiteral}
import com.intellij.openapi.editor.{VisualPosition, Editor}
import com.intellij.openapi.actionSystem.DataContext
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaRecursiveElementVisitor, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeProjection, ScTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.types.ScDesignatorType
import scala.Some
import org.jetbrains.plugins.scala.lang.psi.types.ScFunctionType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTypeDefinition}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.refactoring.HelpID
import java.util

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
      case x: ScReferenceExpression => {
        x.resolve() match {
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
        ScalaPsiElementFactory.createExpressionFromText(e.getText + " _", e.getManager)
      case _ => e
    }
  }

  def addPossibleTypes(scType: ScType, expr: ScExpression): Array[ScType] = {
    val types = new ArrayBuffer[ScType]
    if (scType != null && scType != psi.types.Unit) types += scType
    expr.getTypeWithoutImplicits(TypingContext.empty).foreach(types +=)
    expr.getTypeIgnoreBaseType(TypingContext.empty).foreach(types +=)
    if (scType == psi.types.Unit) types += scType
    if (types.isEmpty) types += psi.types.Any
    types.toArray
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
              def run() {
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
    Some((element, exprType))
  }

  def ensureFileWritable(project: Project, file: PsiFile): Boolean = {
    val virtualFile = file.getVirtualFile
    val readonlyStatusHandler = ReadonlyStatusHandler.getInstance(project)
    val operationStatus = readonlyStatusHandler.ensureFilesWritable(virtualFile)
    !operationStatus.hasReadonlyFiles
  }

  def getOccurrences(element: PsiElement, enclosingContainer: PsiElement): Array[TextRange] = {
    val occurrences: ArrayBuffer[TextRange] = new ArrayBuffer[TextRange]()
    if (enclosingContainer == element) occurrences += enclosingContainer.asInstanceOf[ScExpression].getTextRange
    else
      for (child <- enclosingContainer.getChildren) {
        if (PsiEquivalenceUtil.areElementsEquivalent(child, element, comparator, false)) {
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
          occurrences ++= getOccurrences(element, child)
        }
      }
    occurrences.toArray
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

  private val comparator = new Comparator[PsiElement]() {
    def compare(element1: PsiElement, element2: PsiElement): Int = {
      (element1, element2) match {
        case _ if element1 == element2 => 0
        case (par1: ScParameter, par2: ScParameter) =>
          val name1 = par1.name
          val name2 = par2.name
          if (name1 != null && name2 != null) name1 compareTo name2
          else 1
        case _ => 1

      }
    }
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
    highlightOccurrences(project, occurrences.map({el: PsiElement => el.getTextRange}), editor)
  }

  def showChooser[T <: PsiElement](editor: Editor, elements: Array[T], pass: PsiElement => Unit, title: String,
                                   elementName: T => String, highlightParent: Boolean = false) {
    val highlighter: ScopeHighlighter = new ScopeHighlighter(editor)
    val model: DefaultListModel = new DefaultListModel
    for (element <- elements) {
      model.addElement(element)
    }
    val list: JList = new JList(model)
    list.setCellRenderer(new DefaultListCellRenderer {
      override def getListCellRendererComponent(list: JList, value: Object, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component = {
        val rendererComponent: Component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
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
        pass(list.getSelectedValue.asInstanceOf[T])
      }
    }).addListener(new JBPopupAdapter {
      override def onClosed(event: LightweightWindowEvent) {
        highlighter.dropHighlight()
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
          case Some(expression) => builder.append(getShortText(expression))
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
        val types = n.extendsBlock.superTypes.filter(_ match {
          case ScDesignatorType(clazz: PsiClass) => clazz.qualifiedName != "scala.ScalaObject"
          case _ => true
        })
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
        p.expr match {case Some(expression) => builder.append(getShortText(expression)) case _ =>}
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
          case Some(expression) => builder.append(getShortText(expression))
          case _ =>
        }
      }
      case s: ScSuperReference => builder.append(s.getText)
      case t: ScThisReference => builder.append(t.getText)
      case t: ScThrowStmt => {
        builder.append("throw ")
        t.body match {
          case Some(expression) => builder.append(getShortText(expression))
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
    builder.toString()
  }

  private[refactoring] def getLineText(editor: Editor): String = {
    val lineNumber = editor.getCaretModel.getLogicalPosition.line
    if (lineNumber >= editor.getDocument.getLineCount) return ""
    val caret = editor.getCaretModel.getVisualPosition
    val lineStart = editor.visualToLogicalPosition(new VisualPosition(caret.line, 0))
    val nextLineStart = editor.visualToLogicalPosition(new VisualPosition(caret.line + 1, 0))
    val start = editor.logicalPositionToOffset(lineStart)
    val end = editor.logicalPositionToOffset(nextLineStart)
    editor.getDocument.getText.substring(start, end)
  }

  def invokeRefactoring(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext,
                        refactoringName: String, invokesNext: () => Unit, exprFilter: (ScExpression) => Boolean = e => true) {

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
        editor.getSelectionModel.setSelection(expr.getTextRange.getStartOffset,
          expr.getTextRange.getEndOffset)
        invokesNext()
      }
      if (expressions.length == 0)
        editor.getSelectionModel.selectLineAtCaret()
      else if (expressions.length == 1) {
        chooseExpression(expressions(0))
        return
      } else {
        ScalaRefactoringUtil.showChooser(editor, expressions, elem =>
          chooseExpression(elem.asInstanceOf[ScExpression]), ScalaBundle.message("choose.expression.for", refactoringName), (expr: ScExpression) => {
          ScalaRefactoringUtil.getShortText(expr)
        })
        return
      }
    }
    invokesNext()
  }



  def fileEncloser(startOffset: Int, file: PsiFile): PsiElement = {
    if (file.asInstanceOf[ScalaFile].isScriptFile()) file
    else {
      var res: PsiElement = file.findElementAt(startOffset)
      while (!res.isInstanceOf[ScFunction] && res.getParent != null &&
              !res.getParent.isInstanceOf[ScTypeDefinition] &&
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
  }

  def enclosingContainer(file: PsiFile, textRanges: TextRange*): PsiElement = {
    Option(commonParent(file, textRanges: _*))
            .map(elem => elem.firstChild.getOrElse(elem)) //to make enclosing container non-strict
            .flatMap(_.scopes.toStream.headOption).orNull
  }

  def availableImportAliases(position: PsiElement): Set[(ScReferenceElement, String)] = {

    def getSelectors(holder: ScImportsHolder): Set[(ScReferenceElement, String)] = {
      val result = collection.mutable.Set[(ScReferenceElement, String)]()
      if (holder != null) {
        val importExprs: Seq[ScImportExpr] = holder.getImportStatements.flatMap(_.importExprs)
        importExprs.flatMap(_.selectors).foreach(s => result += ((s.reference, s.importedName)))
        importExprs.filter(_.selectors.isEmpty).flatMap(_.reference).foreach(ref => result += ((ref, ref.refName)))
        result.toSet
      }
      else Set.empty
    }

    if (position != null && position.getLanguage.getID != "Scala")
      throw new IllegalArgumentException("Only for scala")

    var parent = position.getParent
    val aliases = collection.mutable.Set[(ScReferenceElement, String)]()
    while (parent != null) {
      parent match {
        case holder: ScImportsHolder => aliases ++= getSelectors(holder)
        case _ =>
      }
      parent = if (parent.isInstanceOf[PsiFile]) null else parent.getParent
    }
    aliases.filter(_._1.getTextRange.getEndOffset < position.getTextOffset).toSet
  }

  def typeNameWithImportAliases(scType: ScType, position: PsiElement): String = {

    def referencesToReplace(psiElement: PsiElement, aliases: Set[(ScReferenceElement, String)]): Map[ScSimpleTypeElement, (ScReferenceElement, String)] = {
      val result = collection.mutable.Map[ScSimpleTypeElement, (ScReferenceElement, String)]()
      val visitor = new ScalaRecursiveElementVisitor() {
        //Override also visitReferenceExpression! and visitTypeProjection!
        override def visitReference(ref: ScReferenceElement) {
          for {
            alias <- aliases
            if ref.resolve() == alias._1.resolve() || (ref.resolve() == null && ref.refName == alias._1.refName)
            simpleTypeElem = ScalaPsiUtil.getParentOfType(ref, classOf[ScSimpleTypeElement]).asInstanceOf[ScSimpleTypeElement]
            if simpleTypeElem != null
          } {
            result += (simpleTypeElem -> (ref, alias._2))
          }
          super.visitReference(ref)
        }
        override def visitReferenceExpression(ref: ScReferenceExpression) {
          visitReference(ref)
          super.visitReferenceExpression(ref)
        }
        override def visitTypeProjection(proj: ScTypeProjection) {
          visitReference(proj)
          super.visitTypeProjection(proj)
        }
      }
      psiElement.accept(visitor)
      result.filterKeys(ref => ScalaPsiUtil.getParentOfType(ref, classOf[ScSimpleTypeElement]) != null).toMap
    }

    def replaceRefsWithAliases(psiElement: PsiElement, aliases: Map[ScSimpleTypeElement, (ScReferenceElement, String)]) {
      val visitor = new ScalaRecursiveElementVisitor() {
        override def visitSimpleTypeElement(simple: ScSimpleTypeElement) {
          //replace by import aliases
          if (aliases.isDefinedAt(simple)) {
            val (ref, name) = aliases(simple)
            val newRef = ScalaPsiElementFactory.createReferenceFromText(name, position.getManager)
            ref.getParent.getNode.replaceChild(ref.getNode, newRef.getNode)
          } else {
            //replace canonical texts by presentable text
            for (oldRef <- simple.reference; if oldRef.resolve != null) {
              val typeName = simple.calcType.presentableText
              val newRef = ScalaPsiElementFactory.createReferenceFromText(typeName, position.getManager)
              oldRef.getParent.getNode.replaceChild(oldRef.getNode, newRef.getNode)
            }
          }
          super.visitSimpleTypeElement(simple)
        }
      }
      psiElement.accept(visitor)

    }

    if (scType == null) ""
    else {
      val canonicalTypeElem: ScTypeElement =
        ScalaPsiElementFactory.createTypeElementFromText(scType.canonicalText, position.getManager)
      val aliases = availableImportAliases(position)
      val refsWithAliases = referencesToReplace(canonicalTypeElem, aliases)
      val maxRefsWithAliases = refsWithAliases.filterKeys(ref =>
        refsWithAliases.keys.forall(!_.isAncestorOf(ref)))
      replaceRefsWithAliases(canonicalTypeElem, maxRefsWithAliases)
      canonicalTypeElem.getText
    }
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
    val constrBlock = ScalaPsiUtil.getParentOfType(expr, classOf[ScConstrBlock])
    constrBlock match {
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

  private[refactoring] class IntroduceException extends Exception
}