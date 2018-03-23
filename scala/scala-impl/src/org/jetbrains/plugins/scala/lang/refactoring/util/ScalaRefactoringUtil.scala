package org.jetbrains.plugins.scala
package lang
package refactoring
package util

import java.awt.Component
import java.{util => ju}
import javax.swing.event.{ListSelectionEvent, ListSelectionListener}

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.unwrap.ScopeHighlighter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.{EditorColors, EditorColorsManager}
import com.intellij.openapi.editor.markup.{HighlighterLayer, HighlighterTargetArea, RangeHighlighter, TextAttributes}
import com.intellij.openapi.editor.{Editor, RangeMarker, SelectionModel, VisualPosition}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.{JBPopupAdapter, JBPopupFactory, LightweightWindowEvent}
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.psi._
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiTreeUtil.{findElementOfClassAtRange, getParentOfType, isAncestor}
import com.intellij.refactoring.util.CommonRefactoringUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaStubsUtil
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{DesignatorOwner, ScDesignatorType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.util.JListCompatibility

import scala.annotation.tailrec
import scala.collection.JavaConverters._
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
    val fileText = file.charSequence

    while (file.findElementAt(start).isInstanceOf[PsiWhiteSpace] ||
      (file.findElementAt(start).isInstanceOf[PsiComment] && trimComments) ||
      fileText.charAt(start) == '\n' ||
      fileText.charAt(start) == ' ') start = start + 1
    while (file.findElementAt(end - 1).isInstanceOf[PsiWhiteSpace] ||
      (file.findElementAt(end - 1).isInstanceOf[PsiComment] && trimComments) ||
      fileText.charAt(end - 1) == '\n' ||
      fileText.charAt(end - 1) == ' ') end = end - 1
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

    implicit val projectContext = e.projectContext
    if (hasNlToken) e = createExpressionFromText(text.substring(0, i + 1))
    e.getParent match {
      case x: ScMethodCall if x.args.exprs.nonEmpty => createExpressionFromText(e.getText + " _")
      case _ => e
    }
  }

  def addPossibleTypes(scType: ScType, expr: ScExpression): Array[ScType] = {
    val stdTypes = expr.projectContext.stdTypes
    import stdTypes._

    val types = Option(scType).toSeq ++
      expr.getTypeWithoutImplicits().toOption ++
      expr.getTypeIgnoreBaseType.toOption ++
      expr.expectedType()

    val sorted =
      types.map(_.removeAbstracts)
        .distinct
        .filterNot(_ == Nothing)
        .sortWith {
          case (Unit, _) => false //Unit goes to the very end
          case (_, Unit) => true
          case (t1, t2) => t1.conforms(t2)
        }

    if (sorted.isEmpty) {
      if (scType == Nothing) Array(Nothing)
      else Array(Any)
    }
    else sorted.toArray
  }

  def replaceSingletonTypes(scType: ScType): ScType = scType.updateRecursively {
    case tp: DesignatorOwner => tp.tryExtractDesignatorSingleton
  }

  def inTemplateParents(typeElement: ScTypeElement): Boolean = {
    getParentOfType(typeElement, classOf[ScTemplateParents]) != null
  }

  def isInvalid(typeElement: ScTypeElement): Boolean =
    typeElement.getNextSiblingNotWhitespace.isInstanceOf[ScTypeArgs]

  def getTypeElement(file: PsiFile)
                    (implicit selectionModel: SelectionModel): Option[ScTypeElement] =
    getTypeElement(file, selectionModel.getSelectionStart, selectionModel.getSelectionEnd)

  def getTypeElement(file: PsiFile, startOffset: Int, endOffset: Int): Option[ScTypeElement] = {
    val maybeTypeElement = Option(findElementOfClassAtRange(file, startOffset, endOffset, classOf[ScTypeElement]))

    maybeTypeElement.filter { typeElement =>
      typeElement.getTextRange.getEndOffset == endOffset &&
        !isInvalid(typeElement)
    }
  }

  def getOwner(typeElement: PsiElement): ScTypeParametersOwner = PsiTreeUtil.getParentOfType(typeElement, classOf[ScTypeParametersOwner], true)

  def getTypeParameterOwnerList(typeElement: ScTypeElement): Seq[ScTypeParametersOwner] = {
    val ownersArray: ArrayBuffer[ScTypeParametersOwner] = new ArrayBuffer[ScTypeParametersOwner]()
    typeElement.breadthFirst().foreach {
      case x: ScTypeElement if x.calcType.isInstanceOf[TypeParameterType] =>
        val owner = getOwner(x)
        if (owner != null) {
          ownersArray += owner
        }
      case _ =>
    }
    ownersArray
  }

  def getTypeAliasOwnersList(typeElement: ScTypeElement): Seq[ScTypeParametersOwner] = {
    def getTypeAlias(typeElement: ScTypeElement): ScTypeAlias = {
      val firstChild = typeElement.getFirstChild
      firstChild match {
        case reference: ScStableCodeReferenceElement =>
          reference.resolve() match {
            case ta: ScTypeAlias if !ScalaPsiUtil.hasStablePath(ta) =>
              ta
            case _ => null
          }
        case _ => null
      }
    }

    val ownersArray: ArrayBuffer[ScTypeParametersOwner] = new ArrayBuffer[ScTypeParametersOwner]()
    typeElement.breadthFirst().foreach {
      case te: ScTypeElement =>
        val ta = getTypeAlias(te)
        if (ta != null) {
          val owner = getOwner(ta)
          if (owner != null) {
            ownersArray += owner
          }
        }

      case _ => false
    }

    ownersArray
  }

  def getMinOwner(ownres: Array[ScTypeParametersOwner], currentFile: PsiFile): PsiElement = {
    val filtered = ownres.filter((value: ScTypeParametersOwner) => value.getContainingFile == currentFile)
    PsiTreeUtil.findCommonParent(filtered: _*)
  }

  def getExpression(file: PsiFile)
                   (implicit project: Project, editor: Editor): Option[ScExpression] =
    getExpressionWithTypes(file).map(_._1)

  def getExpressionWithTypes(file: PsiFile)
                            (implicit project: Project, editor: Editor): Option[(ScExpression, Array[ScType])] = {
    val selectionModel = editor.getSelectionModel
    getExpressionWithTypes(file, selectionModel.getSelectionStart, selectionModel.getSelectionEnd)
  }

  def getExpressionWithTypes(file: PsiFile, startOffset: Int, endOffset: Int)
                            (implicit project: Project, editor: Editor): Option[(ScExpression, Array[ScType])] = {
    implicit val ctx: ProjectContext = project

    val rangeText = file.charSequence.substring(startOffset, endOffset)

    def selectedInfixExpr(): Option[(ScExpression, Array[ScType])] = {
      val expr = createOptionExpressionFromText(rangeText)(file.getManager)
      expr match {
        case Some(expression: ScInfixExpr) =>
          val op1 = expression.operation
          if (ensureFileWritable(file)(project)) {
            var res: Option[(ScExpression, Array[ScType])] = None
            inWriteCommandAction(project) {
              val document = editor.getDocument
              document.insertString(endOffset, ")")
              document.insertString(startOffset, "(")
              val documentManager: PsiDocumentManager = PsiDocumentManager.getInstance(project)
              documentManager.commitDocument(document)
              val newOpt = getExpressionWithTypes(file, startOffset, endOffset + 2)
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
      createExpressionWithContextFromText(text, lit.getContext, lit) match {
        case newExpr: ScLiteral =>
          val tpe = newExpr.getNonValueType().getOrAny
          Some(newExpr, Array(tpe))
        case _ => None
      }
    }

    val element = findElementOfClassAtRange(file, startOffset, endOffset, classOf[ScExpression])

    if (element == null || element.getTextRange.getEndOffset != endOffset) {
      return selectedInfixExpr() orElse partOfStringLiteral()
    }

    val cachedType = element.`type`().getOrAny

    object ReferenceToFunction {
      def unapply(refExpr: ScReferenceExpression): Option[ScFunction] = refExpr.bind() match {
        case Some(ScalaResolveResult(f: ScFunction, _)) => Some(f)
        case _ => None
      }
    }
    // Handle omitted parentheses in calls to functions with empty parameter list.
    // todo add a test for case with only implicit parameter list.
    val exprType = (element, cachedType) match {
      case (ReferenceToFunction(func), FunctionType(returnType, _)) if (func: ScFunction).parameters.isEmpty => returnType
      case _ => cachedType
    }
    val types = addPossibleTypes(exprType, element).map(replaceSingletonTypes)
    Some((element, types))
  }

  def expressionToIntroduce(expr: ScExpression): ScExpression = {
    def copyExpr = expr.copy.asInstanceOf[ScExpression]
    def liftMethod = createExpressionFromText(expr.getText + " _")(expr.getManager)
    expr match {
      case ref: ScReferenceExpression =>
        ref.resolve() match {
          case fun: ScFunction if fun.paramClauses.clauses.nonEmpty &&
            fun.paramClauses.clauses.head.isImplicit => copyExpr
          case method: PsiMethod if method.parameters.nonEmpty => liftMethod
          case _ => copyExpr
        }
      case _ => copyExpr
    }
  }

  private[this] def ensureFileWritable(file: PsiFile)
                                      (implicit project: Project): Boolean = {
    val virtualFile = file.getVirtualFile
    val readonlyStatusHandler = ReadonlyStatusHandler.getInstance(project)
    val operationStatus = readonlyStatusHandler.ensureFilesWritable(virtualFile)
    !operationStatus.hasReadonlyFiles
  }

  def getOccurrenceRanges(expression: ScExpression, enclosingContainer: PsiElement): Seq[TextRange] = {
    val element = unparExpr(expression)

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

    def getTextOccurrenceInLiterals(text: String, enclosingContainer: PsiElement, filter: ScLiteral => Boolean): Seq[TextRange] = {
      val result = mutable.ArrayBuffer[TextRange]()
      for (child <- enclosingContainer.getChildren) {
        child match {
          case toCheck: ScLiteral if PsiEquivalenceUtil.areElementsEquivalent(element, toCheck) =>
            result += toCheck.getTextRange
          case lit: ScLiteral if filter(lit) => collectOccurrencesInLiteral(lit, text, result)
          case _ => result ++= getTextOccurrenceInLiterals(text, child, filter)
        }
      }
      result
    }
    element match {
      case intrp: ScInterpolatedStringLiteral =>
        val prefix = intrp.reference.fold("")(_.refName)
        val fileText = intrp.getContainingFile.charSequence
        val text = fileText.substring(intrp.contentRange.getStartOffset, intrp.contentRange.getEndOffset)
        val refNameToResolved = mutable.HashMap[String, PsiElement]()
        intrp.depthFirst().foreach {
          case ref: ScReferenceExpression => refNameToResolved += ((ref.refName, ref.resolve()))
          case _ =>
        }
        val filter: ScLiteral => Boolean = {
          case toCheck: ScInterpolatedStringLiteral =>
            toCheck.reference.fold("")(_.refName) == prefix && toCheck.depthFirst().forall {
              case ref: ScReferenceExpression => refNameToResolved.get(ref.refName).contains(ref.resolve())
              case _ => true
            }
          case _ => false
        }
        getTextOccurrenceInLiterals(text, enclosingContainer, filter)
      case lit: ScLiteral if lit.isString =>
        val text = lit.getValue.asInstanceOf[String]
        val filter: ScLiteral => Boolean = {
          case _: ScInterpolatedStringLiteral if text.contains('$') => false
          case l => l.isString
        }
        getTextOccurrenceInLiterals(text, enclosingContainer, filter)
      case _ => getExprOccurrences(element, enclosingContainer).map(_.getTextRange)
    }
  }

  private[this] def getExprOccurrences(element: PsiElement, enclosingContainer: PsiElement): Seq[ScExpression] = {
    val occurrences = mutable.ArrayBuffer[ScExpression]()
    if (enclosingContainer == element) occurrences += enclosingContainer.asInstanceOf[ScExpression]
    else
      for (child <- enclosingContainer.getChildren) {
        if (PsiEquivalenceUtil.areElementsEquivalent(child, element)) {
          child match {
            case x: ScExpression =>
              x.getParent match {
                case y: ScMethodCall if y.args.exprs.isEmpty => occurrences += y
                case _ => occurrences += x
              }
            case _ =>
          }
        } else {
          occurrences ++= getExprOccurrences(element, child)
        }
      }
    occurrences
  }

  def getTypeElementOccurrences(inElement: ScTypeElement, inEnclosingContainer: PsiElement): Array[ScTypeElement] = {

    def getTypeElementOccurrencesHelper(element: ScTypeElement, enclosingContainer: PsiElement): Array[ScTypeElement] = {
      val occurrences: ArrayBuffer[ScTypeElement] = new ArrayBuffer[ScTypeElement]()
      if (enclosingContainer == element)
        occurrences += enclosingContainer.asInstanceOf[ScTypeElement]
      else
        for (child <- enclosingContainer.getChildren) {
          if (PsiEquivalenceUtil.areElementsEquivalent(child, element)) {
            child match {
              case typeElement: ScTypeElement if !inTemplateParents(typeElement) =>
                occurrences += typeElement
              case _ =>
            }
          } else {
            occurrences ++= getTypeElementOccurrencesHelper(element, child)
          }
        }


      occurrences.toArray
    }

    val result = getTypeElementOccurrencesHelper(inElement, inEnclosingContainer)
    if (result.isEmpty) Array(inElement) else result
  }

  def getOccurrencesInInheritors(typeElement: ScTypeElement,
                                 currentElement: ScTypeDefinition,
                                 conflictsReporter: ConflictsReporter,
                                 project: Project,
                                 editor: Editor): (Array[ScTypeElement], Array[ScalaTypeValidator]) = {

    val scope: GlobalSearchScope = GlobalSearchScope.allScope(currentElement.getProject)
    val inheritors = ScalaStubsUtil.getClassInheritors(currentElement, scope)

    def helper(classObject: ScTemplateDefinition,
               occurrencesRes: mutable.MutableList[Array[ScTypeElement]],
               validatorsRes: mutable.MutableList[ScalaTypeValidator]) = {

      val occurrences = getTypeElementOccurrences(typeElement, classObject)
      val validator = ScalaTypeValidator(typeElement, classObject, occurrences.isEmpty)
      occurrencesRes += occurrences
      validatorsRes += validator
    }

    val collectedOccurrences: mutable.MutableList[Array[ScTypeElement]] = mutable.MutableList()
    val collectedValidators: mutable.MutableList[ScalaTypeValidator] = mutable.MutableList()

    inheritors.foreach((x: ScTemplateDefinition) => helper(x, collectedOccurrences, collectedValidators))

    val occurrences: Array[ScTypeElement] = collectedOccurrences.foldLeft(Array[ScTypeElement]())((a, b) => a ++ b)
    val validators: Array[ScalaTypeValidator] = collectedValidators.toArray

    (occurrences, validators)
  }


  def unparExpr(expression: ScExpression): ScExpression = expression match {
    case ScParenthesisedExpr(innerExpression) => unparExpr(innerExpression)
    case ScBlock(inner: ScExpression) => unparExpr(inner)
    case _ => expression
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

  def getCompatibleTypeNames(myTypes: Array[ScType]): ju.LinkedHashMap[String, ScType] = {
    val map = new ju.LinkedHashMap[String, ScType]
    myTypes.foreach(myType => map.put(myType.presentableText, myType))
    map
  }

  def highlightOccurrences(project: Project, occurrences: Seq[TextRange], editor: Editor): Seq[RangeHighlighter] = {
    val highlighters = new ju.ArrayList[RangeHighlighter]

    if (editor != null) {
      val highlightManager = HighlightManager.getInstance(project)
      val colorsScheme = EditorColorsManager.getInstance.getGlobalScheme
      val attributes = colorsScheme.getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES)

      occurrences.foreach { occurrence =>
        highlightManager.addRangeHighlight(editor, occurrence.getStartOffset, occurrence.getEndOffset, attributes, true, highlighters)
      }
    }

    highlighters.asScala
  }

  def highlightOccurrences(occurrences: Seq[PsiElement])
                          (implicit project: Project, editor: Editor): Seq[RangeHighlighter] =
    highlightOccurrences(project, occurrences.map(_.getTextRange), editor)

  def showChooser[T <: PsiElement](editor: Editor, elements: Array[T], pass: T => Unit, title: String,
                                   elementName: T => String, toHighlight: T => PsiElement = (t: T) => t.asInstanceOf[PsiElement]) {
    class Selection {
      val selectionModel = editor.getSelectionModel
      val (start, end) = (selectionModel.getSelectionStart, selectionModel.getSelectionEnd)
      val scheme = editor.getColorsScheme
      val textAttributes = new TextAttributes
      textAttributes.setForegroundColor(scheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR))
      textAttributes.setBackgroundColor(scheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR))
      var selectionHighlighter: RangeHighlighter = null
      val markupModel = editor.getMarkupModel

      def addHighlighter(): Unit = if (selectionHighlighter == null) {
        selectionHighlighter = markupModel.addRangeHighlighter(start, end, HighlighterLayer.SELECTION + 1,
          textAttributes, HighlighterTargetArea.EXACT_RANGE)
      }

      def removeHighlighter(): Unit = if (selectionHighlighter != null) markupModel.removeHighlighter(selectionHighlighter)
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

        val psiElement = toHighlight(element)
        highlighter.highlight(psiElement, ju.Collections.singletonList(psiElement))
      }
    })

    val callback: Runnable = callbackInTransaction(editor.getProject) {
      pass(list.getSelectedValue.asInstanceOf[T])
    }

    val highlighterAdapter = new JBPopupAdapter {
      override def beforeShown(event: LightweightWindowEvent): Unit = {
        selection.addHighlighter()
      }

      override def onClosed(event: LightweightWindowEvent) {
        highlighter.dropHighlight()
        selection.removeHighlighter()
      }
    }
    JBPopupFactory.getInstance.createListPopupBuilder(list)
      .setTitle(title)
      .setMovable(false)
      .setResizable(false)
      .setRequestFocus(true)
      .setItemChoosenCallback(callback)
      .addListener(highlighterAdapter)
      .createPopup
      .showInBestPositionFor(editor)
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
      case _: ScBlock =>
        builder.append("{...}")
      case _: ScDoStmt =>
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
        if (i.elseBranch.isDefined) builder.append(" else {...}")
      case ScGenericInfixNode(left, op, right) =>
        builder.append(getShortText(left))
        builder.append(" ")
        builder.append(getShortText(op))
        builder.append(" ")
        builder.append(getShortText(right.get))
      case i: ScInterpolationPattern =>
        builder.append(getShortText(i.ref))
        builder.append("\"...\"")
      case c: ScConstructorPattern =>
        builder.append(getShortText(c.ref))
        builder.append(c.args.patterns.map(getShortText).mkString("(", ", ", ")"))
      case n: ScNamingPattern =>
        builder.append(n.name)
        builder.append(" @ ")
        builder.append(getShortText(n.named))
      case f: ScFunctionalTypeElement =>
        builder.append(getShortText(f.paramTypeElement))
        builder.append(" => ")
        builder.append(getShortText(f.returnTypeElement.get))
      case l: ScLiteral => builder.append(l.getText)
      case m: ScMatchStmt =>
        m.expr match {
          case Some(expression) => builder.append(getShortText(expression))
          case _ => builder.append("...")
        }
        builder.append(" match {...}")
      case m: ScMethodCall =>
        builder.append(getShortText(m.getInvokedExpr))
        if (m.argumentExpressions.isEmpty) builder.append("()")
        else builder.append("(...)")
      case n: ScNewTemplateDefinition =>
        builder.append("new ")
        val types = n.extendsBlock.superTypes.filter {
          case ScDesignatorType(clazz: PsiClass) => clazz.qualifiedName != "scala.ScalaObject"
          case _ => true
        }
        for (tp <- types) {
          builder.append(tp.presentableText)
          if (tp != types.last) builder.append(" with ")
        }
        n.extendsBlock.templateBody match {
          case Some(_) => builder.append(" {...}")
          case _ =>
        }
      case p : ScGenericParenthesisedNode[_] =>
        builder.append("(")
        p.subNode match {
          case Some(sub) => builder.append(getShortText(sub))
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
        r.expr.map(getShortText)
          .foreach(builder.append)
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
        if (t.catchBlock.isDefined) builder.append(" catch {...}")
        if (t.finallyBlock.isDefined) builder.append(" finally {...}")
      case t: ScTuple =>
        builder.append("(")
        val exprs = t.exprs
        for (expr <- exprs) {
          builder.append(getShortText(expr))
          if (expr != exprs.last) builder.append(", ")
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
        if (u.bindingExpr.isEmpty) builder.append("_")
        else {
          builder.append(getShortText(u.bindingExpr.get))
          builder.append(" _")
        }
      case _: ScUnitExpr => builder.append("()")
      case _: ScWhileStmt => builder.append("while (...) {...}")
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
    editor.getDocument.getImmutableCharSequence.substring(start, end)
  }

  def getExpressionsAtOffset(file: PsiFile, offset: Int): Seq[ScExpression] = {
    val selectedElement = file.findElementAt(offset) match {
      case whiteSpace: PsiWhiteSpace if whiteSpace.getTextRange.getStartOffset == offset &&
        whiteSpace.getText.contains("\n") => file.findElementAt(offset - 1)
      case element => element
    }
    getExpressions(selectedElement)
  }

  private[this] def getExpressions(selectedElement: PsiElement): Seq[ScExpression] = {
    val result = mutable.ArrayBuffer[ScExpression]()
    var parent = selectedElement
    while (parent != null && !parent.getText.contains("\n")) {
      parent match {
        case expression: ScExpression => result += expression
        case _ =>
      }
      parent = parent.getParent
    }
    result
  }

  def afterExpressionChoosing(file: PsiFile,
                              refactoringName: String, filterExpressions: Boolean = true)
                             (invokesNext: => Unit)
                             (implicit project: Project, editor: Editor): Unit =
    invokeOnSelected("choose.expression.for", refactoringName)(invokesNext, (_: ScExpression) => invokesNext) {
      getExpressionsAtOffset(file, editor.getCaretModel.getOffset).filter {
        case e if filterExpressions => checkCanBeIntroduced(e).isEmpty
        case _ => true
      }.toArray
    }

  private[this] def getTypeElements(selectedElement: ScTypeElement): Seq[ScTypeElement] = {
    val result = mutable.ArrayBuffer[ScTypeElement]()
    var parent = selectedElement
    while (parent != null) {
      parent match {
        case simpleType: ScSimpleTypeElement if isInvalid(simpleType) =>
        case ScParenthesisedTypeElement(typeElement) =>
          if (!result.contains(typeElement)) {
            result += typeElement
          }
        case typeElement => result += typeElement
      }
      parent = getParentOfType(parent, classOf[ScTypeElement])
    }
    result
  }

  def afterTypeElementChoosing(selectedElement: ScTypeElement, refactoringName: String)
                              (invokesNext: ScTypeElement => Unit)
                              (implicit project: Project, editor: Editor): Unit =
    invokeOnSelected("choose.type.element.for", refactoringName)(invokesNext(selectedElement), invokesNext) {
      getTypeElements(selectedElement).toArray
    }

  private[this] def invokeOnSelected[T <: ScalaPsiElement](messageKey: String,
                                                           refactoringName: String)
                                                          (default: => Unit, consumer: T => Unit)
                                                          (elements: => Array[T])
                                                          (implicit editor: Editor): Unit = {
    implicit val selectionModel: SelectionModel = editor.getSelectionModel

    if (selectionModel.hasSelection) default
    else {
      def onElement(element: T): Unit = {
        val textRange = element.getTextRange
        selectionModel.setSelection(textRange.getStartOffset, textRange.getEndOffset)

        consumer(element)
      }

      elements match {
        case Array() =>
          selectionModel.selectLineAtCaret()
          default
        case Array(head) => onElement(head)
        case array => showChooser(editor, array, onElement, ScalaBundle.message(messageKey, refactoringName), getShortText)
      }
    }
  }

  def fileEncloser(file: PsiFile)
                  (implicit selectionModel: SelectionModel): Option[PsiElement] =
    fileEncloser(file, selectionModel.getSelectionStart)

  def fileEncloser(file: PsiFile, startOffset: Int): Option[PsiElement] =
    file match {
      case scalaFile: ScalaFile if scalaFile.isScriptFile => Some(file)
      case _ =>
        val maybeElement = Option(file.findElementAt(startOffset))
          .flatMap(element => Option(getParentOfType(element, classOf[ScExtendsBlock], classOf[PsiFile])))

        maybeElement.orElse {
          file.getChildren.find(_.getTextRange.contains(startOffset))
        }
    }

  def isInplaceAvailable(editor: Editor): Boolean =
    editor.getSettings.isVariableInplaceRenameEnabled && !ApplicationManager.getApplication.isUnitTestMode

  def enclosingContainer(parent: PsiElement): PsiElement = {
    Option(parent)
            .map(elem => elem.firstChild.getOrElse(elem)) //to make enclosing container non-strict
            .flatMap(_.scopes.toStream.headOption).orNull
  }

  def commonParent(file: PsiFile, textRange: TextRange): PsiElement =
    commonParent(file, Seq(textRange))

  def commonParent(file: PsiFile, textRanges: Seq[TextRange]): PsiElement = {
    val offsets = textRanges.map(_.getStartOffset) ++ textRanges.map(_.getEndOffset - 1)
    PsiTreeUtil.findCommonParent(offsets.map(file.findElementAt): _*)
  }

  def isLiteralPattern(file: PsiFile, textRange: TextRange): Boolean = {
    val maybeParent = Option(commonParent(file, textRange))
    val maybePattern = maybeParent.flatMap(element => Option(getParentOfType(element, classOf[ScLiteralPattern])))

    maybePattern
      .map(_.getTextRange)
      .contains(textRange)
  }

  def showErrorHintWithException(message: String,
                                 refactoringName: String,
                                 helpId: String = null)
                                (implicit project: Project, editor: Editor): Nothing = {
    showErrorHint(message, refactoringName, helpId)
    throw new IntroduceException
  }

  def showErrorHint(message: String,
                    refactoringName: String,
                    helpId: String = null)
                   (implicit project: Project, editor: Editor): Unit = {
    CommonRefactoringUtil.showErrorHint(project, editor, message, refactoringName, helpId)
  }

  def writableScalaFile(file: PsiFile, refactoringName: String)
                       (implicit project: Project, editor: Editor): ScalaFile =
    file match {
      case scalaFile: ScalaFile if ensureFileWritable(file) => scalaFile
      case _: ScalaFile => showErrorHintWithException(ScalaBundle.message("file.is.not.writable"), refactoringName)
      case _ => showErrorHintWithException(ScalaBundle.message("only.for.scala"), refactoringName)
    }

  def maybeWritableScalaFile(file: PsiFile, refactoringName: String)
                            (implicit project: Project, editor: Editor): Option[ScalaFile] =
    file match {
      case scalaFile: ScalaFile if ensureFileWritable(file) => Some(scalaFile)
      case _: ScalaFile =>
        showErrorHint(ScalaBundle.message("file.is.not.writable"), refactoringName)
        None
      case _ => None
    }

  def checkCanBeIntroduced(expr: ScExpression): Option[String] = {
    val exists1 = expr.parentOfType(classOf[ScConstrBlock], strict = false)
      .flatMap(_.selfInvocation)
      .flatMap(_.args)
      .exists(_.isAncestorOf(expr))

    if (exists1) {
      return Some(ScalaBundle.message("cannot.refactor.arg.in.self.invocation.of.constructor"))
    }

    val exists2 = expr.parentOfType(classOf[ScGuard], strict = false)
      .exists(_.getParent.isInstanceOf[ScCaseClause])
    if (exists2) {
      return Some(ScalaBundle.message("refactoring.is.not.supported.in.guard"))
    }

    expr match {
      case block: ScBlock if !block.hasRBrace && block.statements.size != 1 =>
        return Some(ScalaBundle.message("cannot.refactor.not.expression"))
      case _ =>
    }

    val errorMessage = expr.getParent match {
      case ScInfixExpr(_, operation, _) if operation == expr => ScalaBundle.message("cannot.refactor.not.expression")
      case ScPostfixExpr(_, operation) if operation == expr => ScalaBundle.message("cannot.refactor.not.expression")
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

    Option(errorMessage)
  }

  def replaceOccurence(textRange: TextRange, newString: String, file: PsiFile): RangeMarker = {
    val documentManager = PsiDocumentManager.getInstance(file.getProject)
    val document = documentManager.getDocument(file)
    var shift = 0
    val start = textRange.getStartOffset
    document.replaceString(start, textRange.getEndOffset, newString)
    val newRange = new TextRange(start, start + newString.length)
    documentManager.commitDocument(document)
    val leaf = file.findElementAt(start)
    val parent = leaf.getParent
    parent match {
      case null =>
      case ChildOf(pars@ScParenthesisedExpr(inner)) if !ScalaPsiUtil.needParentheses(pars, inner) =>
        val textRange = pars.getTextRange
        val afterWord = textRange.getStartOffset > 0 && {
          val prevElemType = file.findElementAt(textRange.getStartOffset - 1).getNode.getElementType
          ScalaTokenTypes.IDENTIFIER_TOKEN_SET.contains(prevElemType) || ScalaTokenTypes.KEYWORDS.contains(prevElemType)
        }
        shift = pars.getTextRange.getStartOffset - inner.getTextRange.getStartOffset + (if (afterWord) 1 else 0)
        document.replaceString(textRange.getStartOffset, textRange.getEndOffset, (if (afterWord) " " else "") + newString)
      case ChildOf(ScPostfixExpr(_, `parent`)) =>
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
          val withNextChar = file.charSequence.substring(newRange.getStartOffset, newRange.getEndOffset + 1)
          val needBraces = isIdentifier(withNextChar) && withNextChar.last != '$'
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
    val newExpr = findElementOfClassAtRange(file, newStart, newEnd, classOf[ScExpression])
    val newPattern = PsiTreeUtil.findElementOfClassAtOffset(file, newStart, classOf[ScPattern], true)
    Option(newExpr).orElse(Option(newPattern))
      .map(elem => document.createRangeMarker(elem.getTextRange))
      .getOrElse(throw new IntroduceException)
  }


  def replaceOccurences(occurrences: Seq[TextRange], newString: String, file: PsiFile): Seq[TextRange] = {
    val revercedRangeMarkers = occurrences.reverseMap(replaceOccurence(_, newString, file))
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
        case _: ScForStatement => true
        case _ => false
      }
      result || needBraces(parExpr, nextParent)
    }
    val interpolated = Option(PsiTreeUtil.getParentOfType(elem, classOf[ScInterpolatedStringLiteral], false))
    val expr = interpolated getOrElse PsiTreeUtil.getParentOfType(elem, classOf[ScExpression], false)
    val nextPar = nextParent(expr, elem.getContainingFile)
    nextPar match {
      case prevExpr: ScExpression if !checkEnd(nextPar, expr) => findParentExpr(prevExpr)
      case _: ScExpression if checkEnd(nextPar, expr) => expr
      case _ => expr
    }
  }

  def findParentExpr(file: PsiFile, range: TextRange): ScExpression =
    findParentExpr(commonParent(file, range))

  def nextParent(expr: PsiElement, file: PsiFile): PsiElement = {
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
      case (_: ScFunction) && (_ childOf (_: ScTemplateBody | _: ScEarlyDefinitions)) => true
      case ifSt: ScIfStmt if Seq(ifSt.thenBranch, ifSt.elseBranch) contains Option(parExpr) => true
      case forSt: ScForStatement if forSt.body.orNull == parExpr => true
      case _: ScForStatement => false
      case _: ScEnumerator | _: ScGenerator => false
      case guard: ScGuard if guard.getParent.isInstanceOf[ScEnumerators] => false
      case whSt: ScWhileStmt if whSt.body.orNull == parExpr => true
      case doSt: ScDoStmt if doSt.getExprBody.orNull == parExpr => true
      case finBl: ScFinallyBlock if finBl.expression.orNull == parExpr => true
      case fE: ScFunctionExpr =>
        fE.getContext match {
          case be: ScBlock if be.lastExpr.contains(fE) => false
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
            val newRef = createExpressionFromText(ref.getText, position).asInstanceOf[ScReferenceExpression]
            result &= ref.resolve() == newRef.resolve()
        }
        super.visitReferenceExpression(ref)
      }
    }
    expr.accept(visitor)
    result
  }

  @tailrec
  def container(element: PsiElement): Option[PsiElement] = if (element != null) {
    val maybeFunction = element.parentOfType(classOf[ScFunctionDefinition])
    val maybeBody = maybeFunction.flatMap(_.body).filter {
      case _: ScBlock |
           _: ScNewTemplateDefinition => false
      case _ => true
    }

    val classes = Seq(classOf[ScalaFile], classOf[ScBlock], classOf[ScTemplateBody], classOf[ScCaseClause], classOf[ScEarlyDefinitions])
    element.nonStrictParentOfType(classes) match {
      case Some(candidate) if maybeBody.isDefined && candidate.isAncestorOf(maybeFunction.get) => maybeBody
      case Some(block: ScBlock) if block.hasCaseClauses => container(block.getContext)
      case maybeCandidate => maybeCandidate
    }
  } else None

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

  def showNotPossibleWarnings(elements: Seq[PsiElement], refactoringName: String)
                             (implicit project: Project, editor: Editor): Boolean = {
    def errors(elem: PsiElement): Option[String] = elem match {
      case funDef: ScFunctionDefinition if hasOutsideUsages(funDef) => ScalaBundle.message("cannot.extract.used.function.definition").toOption
      case _: ScBlockStatement => None
      case comm: PsiComment if !comm.getParent.isInstanceOf[ScMember] => None
      case _: PsiWhiteSpace => None
      case _ if ScalaTokenTypes.tSEMICOLON == elem.getNode.getElementType => None
      case typeDef: ScTypeDefinition if hasOutsideUsages(typeDef) => ScalaBundle.message("cannot.extract.used.type.definition").toOption
      case _: ScSelfInvocation => ScalaBundle.message("cannot.extract.self.invocation").toOption
      case _ => ScalaBundle.message("cannot.extract.empty.message").toOption
    }

    def hasOutsideUsages(elem: PsiElement): Boolean =
      !elem.parentOfType(classOf[ScConstructorOwner])
        .map(new LocalSearchScope(_)).toSeq
        .flatMap(scope => ReferencesSearch.search(elem, scope).findAll().asScala)
        .map(_.getElement)
        .forall(referenced => elements.exists(isAncestor(_, referenced, false)))

    val messages = elements.flatMap(errors).distinct
    if (messages.nonEmpty) {
      showErrorHint(messages.mkString("\n"), refactoringName)
      return true
    }

    if (elements.isEmpty || !elements.exists(_.isInstanceOf[ScBlockStatement])) {
      showErrorHint(ScalaBundle.message("cannot.extract.empty.message"), refactoringName)
      return true
    }

    false
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
