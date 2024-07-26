package org.jetbrains.plugins.scala.lang.refactoring.util

import com.intellij.codeInsight.PsiEquivalenceUtil
import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.codeInsight.unwrap.ScopeHighlighter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.{EditorColors, EditorColorsScheme}
import com.intellij.openapi.editor.markup._
import com.intellij.openapi.editor.{Document, Editor, RangeMarker, SelectionModel}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.{JBPopup, JBPopupFactory, JBPopupListener, LightweightWindowEvent}
import com.intellij.openapi.util.{Key, TextRange}
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi._
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.search.{GlobalSearchScope, LocalSearchScope}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.util.Consumer
import org.jetbrains.annotations.{Nls, TestOnly}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.stubs.util.ScalaInheritors
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.ScTypePresentationExt
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isIdentifier
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectExt

import java.awt.Component
import java.util.Collections
import java.{lang, util => ju}
import javax.swing.{DefaultListCellRenderer, JList, ListCellRenderer}
import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._

object ScalaRefactoringUtil {

  def trimSelectionOffsets(file: PsiFile, startOffset: Int, endOffset: Int, trimComments: Boolean): (Int, Int) = {
    assert(startOffset <= endOffset, "range start offset must not be greater than end offset")
    assert(endOffset <= file.getTextLength, "range end offset must not be greater than the file length")

    var stop = false
    var start = startOffset
    while (!stop && start < endOffset)
      file.findElementAt(start) match {
        case comment: PsiComment if trimComments => start = comment.endOffset
        case whitespace: PsiWhiteSpace           => start = whitespace.endOffset
        case _                                   => stop = true
      }

    if (start >= endOffset) {
      (startOffset, startOffset)
    } else {
      stop = false
      var end = endOffset
      while (!stop && end > start)
        file.findElementAt(end - 1) match {
          case comment: PsiComment if trimComments => end = comment.getTextOffset
          case whitespace: PsiWhiteSpace           => end = whitespace.getTextOffset
          case _                                   => stop = true
        }

      (start, end)
    }
  }

  def trimSpacesAndComments(editor: Editor, file: PsiFile, trimComments: Boolean = true): Unit = {
    val selectionModel = editor.getSelectionModel
    val startOffset = selectionModel.getSelectionStart
    val endOffset = selectionModel.getSelectionEnd

    val (start, end) = trimSelectionOffsets(file, startOffset, endOffset, trimComments)

    if (start != startOffset || end != endOffset)
      selectionModel.setSelection(start, end)
  }

  private def inTemplateParents(typeElement: ScTypeElement): Boolean = {
    PsiTreeUtil.getParentOfType(typeElement, classOf[ScTemplateParents]) != null
  }

  def isInvalid(typeElement: ScTypeElement): Boolean =
    typeElement.getNextSiblingNotWhitespace.is[ScTypeArgs]

  def getSelectedTypeElement(file: PsiFile)
                            (implicit editor: Editor): Option[ScTypeElement] = {
    val selectionModel = editor.getSelectionModel
    getTypeElement(file, selectionModel.getSelectionStart, selectionModel.getSelectionEnd)
  }

  def getTypeElement(file: PsiFile, startOffset: Int, endOffset: Int): Option[ScTypeElement] = {
    val maybeTypeElement = Option(PsiTreeUtil.findElementOfClassAtRange(file, startOffset, endOffset, classOf[ScTypeElement]))
    maybeTypeElement.filter { typeElement =>
      typeElement.getTextRange.getEndOffset == endOffset &&
        !isInvalid(typeElement)
    }
  }

  def getOwner(typeElement: PsiElement): ScTypeParametersOwner =
    PsiTreeUtil.getParentOfType(typeElement, classOf[ScTypeParametersOwner], true)

  def getTypeParameterOwnerList(typeElement: ScTypeElement): Seq[ScTypeParametersOwner] = {
    val ownersBuilder = Seq.newBuilder[ScTypeParametersOwner]
    typeElement.breadthFirst().foreach {
      case x: ScTypeElement if x.calcType.is[TypeParameterType] =>
        val owner = getOwner(x)
        if (owner != null) {
          ownersBuilder += owner
        }
      case _ =>
    }
    ownersBuilder.result()
  }

  def getTypeAliasOwnersList(typeElement: ScTypeElement): Seq[ScTypeParametersOwner] = {
    def getTypeAlias(typeElement: ScTypeElement): ScTypeAlias = {
      val firstChild = typeElement.getFirstChild
      firstChild match {
        case reference: ScStableCodeReference =>
          reference.resolve() match {
            case ta: ScTypeAlias if !ScalaPsiUtil.hasStablePath(ta) =>
              ta
            case _ => null
          }
        case _ => null
      }
    }

    val ownersBuilder = Seq.newBuilder[ScTypeParametersOwner]
    typeElement.breadthFirst().foreach {
      case te: ScTypeElement =>
        val ta = getTypeAlias(te)
        if (ta != null) {
          val owner = getOwner(ta)
          if (owner != null) {
            ownersBuilder += owner
          }
        }

      case _ => false
    }

    ownersBuilder.result()
  }

  def getSelectedExpression(file: PsiFile)
                           (implicit project: Project, editor: Editor): Option[ScExpression] =
    getSelectedExpressionWithTypes(file).map(_._1)

  def getSelectedExpressionWithTypes(file: PsiFile)
                                    (implicit project: Project, editor: Editor): Option[(ScExpression, ArraySeq[ScType])] = {
    val selectionModel = editor.getSelectionModel
    getExpressionWithTypes(file, editor.getDocument, selectionModel.getSelectionStart, selectionModel.getSelectionEnd)
  }

  def getExpressionWithTypes(file: PsiFile, document: Document, start: Int, end: Int, trimText: Boolean = true)
                            (implicit project: Project): Option[(ScExpression, ArraySeq[ScType])] = {
    val (startOffset, endOffset) =
      if (trimText)
        trimSelectionOffsets(file, start, end, trimComments = false)
      else
        (start, end)
    val rangeText = file.charSequence.substring(startOffset, endOffset)

    def selectedInfixExpr(): Option[(ScExpression, ArraySeq[ScType])] = {
      val expr = createOptionExpressionFromText(rangeText, file)(file.getManager)
      expr match {
        case Some(expression: ScInfixExpr) =>
          val op1 = expression.operation
          if (ensureFileWritable(file)) {
            var res: Option[(ScExpression, ArraySeq[ScType])] = None
            inWriteCommandAction {
              val documentManager: PsiDocumentManager = PsiDocumentManager.getInstance(project)

              document.insertString(endOffset, ")")
              document.insertString(startOffset, "(")
              documentManager.commitDocument(document)

              val newOpt = getExpressionWithTypes(file, document, startOffset, endOffset + 2, trimText = false)
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
            res
          } else None
        case _ =>  None
      }
    }

    def partOfStringLiteral(): Option[(ScExpression, ArraySeq[ScType])] = {
      val literalStart = PsiTreeUtil.findElementOfClassAtOffset(file, startOffset, classOf[ScStringLiteral], false)
      val literalEnd = PsiTreeUtil.findElementOfClassAtOffset(file, endOffset, classOf[ScStringLiteral], false)
      if (literalStart == null || !literalStart.hasValidClosingQuotes || literalStart != literalEnd) return None

      val prefix = literalStart match {
        case intrp: ScInterpolatedStringLiteral if rangeText.contains('$') => intrp.referenceName
        case _ => ""
      }
      val quote = if (literalStart.isMultiLineString) "\"\"\"" else "\""

      val text = s"$prefix$quote$rangeText$quote"
      createExpressionWithContextFromText(text, literalStart.getContext, literalStart) match {
        case newExpr: ScLiteral =>
          val tpe = newExpr.getTypeWithoutImplicits(ignoreBaseType = true).getOrAny
          Some(newExpr, ArraySeq(tpe))
        case _ => None
      }
    }

    val elementsAtRange = ScalaPsiUtil.elementsAtRange[ScExpression](file, startOffset, endOffset)

    val expression = elementsAtRange.find(canBeIntroduced).orNull

    if (expression == null || expression.endOffset != endOffset) {
      return selectedInfixExpr() orElse partOfStringLiteral()
    }

    val typeNoExpected = typeWithoutExpected(expression)

    Some((expression, ArraySeq(typeNoExpected)))
  }

  def expressionToIntroduce(expr: ScExpression): ScExpression = {
    def copyExpr = expr.copy.asInstanceOf[ScExpression]
    def liftMethod = createExpressionFromText(expr.getText + " _", expr)(expr.getManager)

    @tailrec
    def needToLift(srr: ScalaResolveResult): Boolean = srr match {
      case ScalaResolveResult.ApplyMethodInnerResolve(inner) => needToLift(inner)
      case ScalaResolveResult(fun: ScFunction, _) if fun.paramClauses.clauses.nonEmpty &&
        fun.paramClauses.clauses.head.isImplicit => false
      case ScalaResolveResult(method: PsiMethod, _) if method.hasParameters => true
      case _                                                                => false
    }
    expr match {
      case ref: ScReferenceExpression =>
        val lift = ref.bind().exists(needToLift)

        if (lift) liftMethod
        else      copyExpr
      case _ => copyExpr
    }
  }

  private def typeWithoutExpected(expression: ScExpression): ScType = {
    def dummyFunctionText(needsParens: Boolean) =
      s"def __dummyFunction__ = {\n ${expression.getText.parenthesize(needsParens)} \n}"

    def createFunction(needsParens: Boolean) =
      createMethodWithContext(dummyFunctionText(needsParens), expression.getContext, expression)
        .asInstanceOf[ScFunctionDefinition]

    val definitionWithoutType =
      Option(createFunction(false))
        // if body is not parsed correctly, try wrapping in parentheses
        .filter(fn => fn.body.filterByType[ScBlockExpr]
          .exists(block => !PsiTreeUtil.hasErrorElements(block) && block.statements.lengthIs == 1))
        .getOrElse(createFunction(true))

    definitionWithoutType.`type`().getOrAny
  }

  private def ensureFileWritable(file: PsiFile)
                                (implicit project: Project): Boolean = {
    val virtualFile = file.getVirtualFile
    val readonlyStatusHandler = ReadonlyStatusHandler.getInstance(project)
    val operationStatus = readonlyStatusHandler.ensureFilesWritable(Collections.singletonList(virtualFile))
    !operationStatus.hasReadonlyFiles
  }

  def getOccurrenceRanges(expression: ScExpression, enclosingContainer: PsiElement): Seq[TextRange] = {
    val element = unparExpr(expression)

    def collectOccurrencesInLiteral(literal: ScLiteral, text: String, result: mutable.Growable[TextRange]): Unit = {
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
      val builder = Seq.newBuilder[TextRange]
      for (child <- enclosingContainer.getChildren) {
        child match {
          case toCheck: ScLiteral if PsiEquivalenceUtil.areElementsEquivalent(element, toCheck) =>
            builder += toCheck.getTextRange
          case lit: ScLiteral if filter(lit) => collectOccurrencesInLiteral(lit, text, builder)
          case _ => builder ++= getTextOccurrenceInLiterals(text, child, filter)
        }
      }
      builder.result()
    }

    element match {
      case intrp: ScInterpolatedStringLiteral =>
        val prefix = intrp.referenceName
        val fileText = intrp.getContainingFile.charSequence
        val contentRange = intrp.contentRange
        val text = fileText.substring(contentRange.getStartOffset, contentRange.getEndOffset)
        val refNameToResolved = mutable.HashMap[String, PsiElement]()
        intrp.depthFirst().foreach {
          case ref: ScReferenceExpression => refNameToResolved += ((ref.refName, ref.resolve()))
          case _ =>
        }
        val filter: ScLiteral => Boolean = {
          case toCheck: ScInterpolatedStringLiteral =>
            toCheck.referenceName == prefix && toCheck.depthFirst().forall {
              case ref: ScReferenceExpression => refNameToResolved.get(ref.refName).contains(ref.resolve())
              case _ => true
            }
          case _ => false
        }
        getTextOccurrenceInLiterals(text, enclosingContainer, filter)
      case lit: ScStringLiteral if lit.hasValidClosingQuotes =>
        val text = lit.getValue.asInstanceOf[String]
        val filter: ScLiteral => Boolean = {
          case _: ScInterpolatedStringLiteral if text.contains('$') => false
          case s: ScStringLiteral => s.hasValidClosingQuotes
          case _ => false
        }
        getTextOccurrenceInLiterals(text, enclosingContainer, filter)
      case _ => getExprOccurrences(element, enclosingContainer).map(_.getTextRange)
    }
  }

  private def getExprOccurrences(element: PsiElement, enclosingContainer: PsiElement): Seq[ScExpression] = {
    val occurrencesBuilder = Seq.newBuilder[ScExpression]
    if (enclosingContainer == element) occurrencesBuilder += enclosingContainer.asInstanceOf[ScExpression]
    else
      for (child <- enclosingContainer.getChildren) {
        if (PsiEquivalenceUtil.areElementsEquivalent(child, element)) {
          child match {
            case x: ScExpression =>
              x.getParent match {
                case y: ScMethodCall if y.args.exprs.isEmpty => occurrencesBuilder += y
                case _ => occurrencesBuilder += x
              }
            case _ =>
          }
        } else {
          occurrencesBuilder ++= getExprOccurrences(element, child)
        }
      }
    occurrencesBuilder.result()
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
                                 currentElement: ScTypeDefinition
                                ): (Array[ScTypeElement], Array[ScalaTypeValidator]) = {

    val scope: GlobalSearchScope = GlobalSearchScope.allScope(currentElement.getProject)
    val inheritors = ScalaInheritors.directInheritorCandidates(currentElement, scope)

    def helper(classObject: ScTemplateDefinition,
               occurrencesRes: mutable.ListBuffer[Array[ScTypeElement]],
               validatorsRes: mutable.ListBuffer[ScalaTypeValidator]) = {

      val occurrences = getTypeElementOccurrences(typeElement, classObject)
      val validator = ScalaTypeValidator(typeElement, classObject, occurrences.isEmpty)
      occurrencesRes += occurrences
      validatorsRes += validator
    }

    val collectedOccurrences: mutable.ListBuffer[Array[ScTypeElement]] = mutable.ListBuffer()
    val collectedValidators: mutable.ListBuffer[ScalaTypeValidator] = mutable.ListBuffer()

    inheritors.foreach((x: ScTemplateDefinition) => helper(x, collectedOccurrences, collectedValidators))

    val occurrences: Array[ScTypeElement] = collectedOccurrences.foldLeft(Array[ScTypeElement]())((a, b) => a ++ b)
    val validators: Array[ScalaTypeValidator] = collectedValidators.toArray

    (occurrences, validators)
  }

  @scala.annotation.tailrec
  def unparExpr(expression: ScExpression): ScExpression = expression match {
    case ScParenthesisedExpr(innerExpression) => unparExpr(innerExpression)
    case ScBlock(inner: ScExpression) => unparExpr(inner)
    case _ => expression
  }

  def getCompatibleTypeNames(myTypes: Seq[ScType])
                            (implicit context: TypePresentationContext): ju.LinkedHashMap[String, ScType] = {
    val map = new ju.LinkedHashMap[String, ScType]
    myTypes.foreach(myType => map.put(myType.codeText, myType))
    map
  }

  private[refactoring]
  def highlightOccurrences(
    project: Project,
    occurrences: Iterable[TextRange],
    editor: Editor,
  ): Seq[RangeHighlighter] = {
    if (editor != null) {
      val highlightersBuilder = new java.util.ArrayList[RangeHighlighter]
      val highlightManager = HighlightManager.getInstance(project)
      val attributes = EditorColors.SEARCH_RESULT_ATTRIBUTES

      occurrences.foreach { occurrence =>
        highlightManager.addRangeHighlight(
          editor,
          occurrence.getStartOffset,
          occurrence.getEndOffset,
          attributes,
          true,
          highlightersBuilder
        )
      }

      highlightersBuilder.asScala.toSeq
    }
    else Nil
  }

  private[refactoring]
  def highlightOccurrences(
    project: Project,
    occurrences: Seq[PsiElement],
    editor: Editor
  ): Seq[RangeHighlighter] = {
    val ranges = occurrences.map(_.getTextRange)
    highlightOccurrences(project, ranges, editor)
  }

  /**
   * '''ATTENTION''': for PSI elements use [[showPsiChooser]]
   */
  private[scala] def showChooserGeneric[T](
    elements: Seq[T],
    onChosen: T => Unit,
    @Nls title: String,
    presentation: T => String,
    toHighlight: T => PsiElement
  )(implicit project: Project, editor: Editor): Unit =
    showChooserImpl(onChosen) { chooserModel =>
      JBPopupFactory.getInstance
        .createPopupChooserBuilder(elements.asJava)
        .setTitle(title)
        .setMovable(false)
        .setResizable(false)
        .setRequestFocus(true)
        .addListener(chooserModel.highlighterPopupListener)
        .setItemChosenCallback(chooserModel.elementChosen)
        .setItemSelectedCallback { item =>
          val psiElement = toHighlight(item)
          if (psiElement != null) {
            chooserModel.highlightPsi.accept(psiElement)
          }
        }
        .setRenderer(new DefaultListCellRenderer {
          override def getListCellRendererComponent(list: JList[_], value: Object, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component = {
            val rendererComponent: Component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            val element: T = value.asInstanceOf[T]
            setText(presentation(element))
            rendererComponent
          }
        }.asInstanceOf[ListCellRenderer[T]])
        .createPopup
    }

  def showPsiChooser[T <: PsiElement](elements: Seq[T],
                                      onChosen: T => Unit,
                                      @Nls title: String,
                                      presentation: T => String,
                                      toHighlight: PsiElement => PsiElement = identity)
                                     (implicit project: Project, editor: Editor): Unit =
    showChooserImpl(onChosen) { chooserModel =>
      new PsiTargetNavigator(elements.asJava)
        .presentationProvider((element: T) => TargetPresentation.builder(presentation(element)).presentation())
        .builderConsumer { builder =>
          builder
            .setMovable(false)
            .setResizable(false)
            .setRequestFocus(true)
            .addListener(chooserModel.highlighterPopupListener)
            .setItemSelectedCallback { item =>
              if (item != null) {
                val element = item.dereference()
                if (element != null) {
                  val psiElement = toHighlight(element)
                  if (psiElement != null) {
                    chooserModel.highlightPsi.accept(psiElement)
                  }
                }
              }
            }
        }
        .createPopup(project, title, (element: T) => {
          chooserModel.elementChosen.accept(element)
          true
        })
    }

  /** See [[showChooserImpl]] */
  private final case class HighlightingChooserModel[T](
    elementChosen: Consumer[T],
    highlightPsi: Consumer[PsiElement],
    highlighterPopupListener: JBPopupListener,
  )

  private def showChooserImpl[T](onChosen: T => Unit)
                                (createPopup: HighlightingChooserModel[T] => JBPopup)
                                (implicit project: Project, editor: Editor): Unit = {
    class Selection {
      private val selectionModel: SelectionModel = editor.getSelectionModel
      private val (start, end) = (selectionModel.getSelectionStart, selectionModel.getSelectionEnd)
      private val scheme: EditorColorsScheme = editor.getColorsScheme
      private val textAttributes = new TextAttributes
      private var selectionHighlighter: RangeHighlighter = _
      private val markupModel: MarkupModel = editor.getMarkupModel

      locally {
        textAttributes.setForegroundColor(scheme.getColor(EditorColors.SELECTION_FOREGROUND_COLOR))
        textAttributes.setBackgroundColor(scheme.getColor(EditorColors.SELECTION_BACKGROUND_COLOR))
      }

      def addHighlighter(): Unit = if (selectionHighlighter == null) {
        selectionHighlighter = markupModel.addRangeHighlighter(start, end, HighlighterLayer.SELECTION + 1,
          textAttributes, HighlighterTargetArea.EXACT_RANGE)
      }

      def removeHighlighter(): Unit = if (selectionHighlighter != null) markupModel.removeHighlighter(selectionHighlighter)
    }

    val selection = new Selection
    val highlighter: ScopeHighlighter = new ScopeHighlighter(editor)

    val chooserModel = HighlightingChooserModel[T](
      elementChosen = element => invokeLaterInTransaction(project.unloadAwareDisposable) {
        onChosen(element)
      },
      highlightPsi = psiElement => {
        highlighter.dropHighlight()
        if (psiElement != null) {
          highlighter.highlight(psiElement, ju.Collections.singletonList(psiElement))
        }
      },
      highlighterPopupListener = new JBPopupListener {
        override def beforeShown(event: LightweightWindowEvent): Unit = selection.addHighlighter()

        override def onClosed(event: LightweightWindowEvent): Unit = {
          highlighter.dropHighlight()
          selection.removeHighlighter()
        }
      }
    )

    val popup = createPopup(chooserModel)
    popup.showInBestPositionFor(editor)
  }

  def getShortText(expr: ScalaPsiElement): String = {
    val builder = new mutable.StringBuilder
    expr match {
      case ass: ScAssignment =>
        builder.append(getShortText(ass.leftExpression))
        builder.append(" = ")
        ass.rightExpression match {
          case Some(r) => builder.append(getShortText(r))
          case _ =>
        }
      case _: ScBlock =>
        builder.append("{...}")
      case _: ScDo =>
        builder.append("do {...} while (...)")
      case f: ScFor =>
        builder.append("for (...) ")

        val yieldOrDo = f.yieldOrDoKeyword
        val yieldOrDoStr = yieldOrDo.map(_.elementType).map(_.getDebugName + " ")
        yieldOrDoStr.foreach(builder.append)

        builder.append("{...}")
      case f: ScFunctionExpr =>
        val arrow = ScalaPsiUtil.functionArrow(f.getProject)
        builder.append(f.params.getText).append(s" $arrow {...}")
      case g: ScGenericCall =>
        builder.append(getShortText(g.referencedExpr))
        builder.append("[...]")
      case i: ScIf =>
        builder.append("if (...) {...}")
        if (i.elseExpression.isDefined) builder.append(" else {...}")
      //
      case ScInfixElement(left, op, rightOption) =>
        rightOption match {
          case Some(right) =>
            builder.append(getShortText(left))
            builder.append(" ")
            builder.append(getShortText(op))
            builder.append(" ")
            builder.append(getShortText(right))
          case _ =>
        }
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
        builder.append(f.returnTypeElement.map(getShortText(_)).getOrElse("..."))
      case l: ScLiteral => builder.append(l.getText)
      case m: ScMatch =>
        m.expression match {
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
        val types = n.extendsBlock.superTypes
        for (tp <- types) {
          builder.append(tp.codeText(expr))
          if (tp != types.last) builder.append(" with ")
        }
        n.extendsBlock.templateBody match {
          case Some(_) => builder.append(" {...}")
          case _ =>
        }
      case p : ScParenthesizedElement =>
        builder.append("(")
        p.innerElement match {
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
      case r: ScReturn =>
        builder.append("return ")
        r.expr.map(getShortText)
          .foreach(builder.append)
      case s: ScSuperReference => builder.append(s.getText)
      case t: ScThisReference => builder.append(t.getText)
      case t: ScThrow =>
        builder.append("throw ")
        t.expression match {
          case Some(expression) => builder.append(getShortText(expression))
          case _ => builder.append("...")
        }
      case t: ScTry =>
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
      case t: ScTypedExpression =>
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
      case _: ScWhile => builder.append("while (...) {...}")
      case x: ScXmlExpr => builder.append(x.getText)
      case _ => builder.append(expr.getText)
    }
    builder.toString()
  }

  /**
   * Collects expressions in the same line that may be extracted as variable or parameter
   */
  def possibleExpressionsToExtract(file: PsiFile, offset: Int): Seq[ScExpression] = {
    val exprWithReasons = possibleExpressionsWithCantIntroduceReason(file, offset)
    exprWithReasons.filter(_._2.isEmpty).map(_._1)
  }

  @TestOnly
  def possibleExpressionsWithCantIntroduceReason(file: PsiFile, offset: Int): Seq[(ScExpression, Option[String])] = {
    val selectedElement = file.findElementAt(offset) match {
      case whiteSpace: PsiWhiteSpace if whiteSpace.getTextRange.getStartOffset == offset &&
        whiteSpace.getText.contains("\n") => file.findElementAt(offset - 1)
      case element => element
    }
    val expressions = getExpressions(selectedElement)
    expressions.map(e => (e, cannotBeIntroducedReason(e)))
  }

  private[this] def getExpressions(selectedElement: PsiElement): Seq[ScExpression] =
    selectedElement.withParentsInFile
      .takeWhile(e => !isBlockLike(e))
      .filterByType[ScExpression]
      .toSeq

  def isBlockLike(e: PsiElement): Boolean = e match {
    case null => true
    case (_: ScBlock) childOf (_: ScInterpolatedStringLiteral) => false
    case _: ScBlock | _: ScTemplateBody | _: ScEarlyDefinitions | _: PsiFile => true
    case _ => false
  }

  def isLastInNonUnitBlock(expr: ScExpression): Boolean = {
    expr.getParent match {
      case b: ScBlock => b.resultExpression.contains(expr) && b.expectedType().exists(!_.isUnit)
      case _          => false
    }
  }

  def afterExpressionChoosing(file: PsiFile, refactoringName: String)
                             (invokesNext: => Unit)
                             (implicit project: Project, editor: Editor): Unit = {
    invokeOnSelected(ScalaBundle.message("choose.expression.for", refactoringName))(invokesNext, (_: ScExpression) => invokesNext) {
      possibleExpressionsToExtract(file, editor.getCaretModel.getOffset)
    }
  }

  private def getTypeElements(selectedElement: ScTypeElement): Seq[ScTypeElement] = {
    val result = mutable.ArrayBuffer.empty[ScTypeElement]
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
      parent = PsiTreeUtil.getParentOfType(parent, classOf[ScTypeElement])
    }
    result.toSeq
  }

  def afterTypeElementChoosing(selectedElement: ScTypeElement, refactoringName: String)
                              (invokesNext: ScTypeElement => Unit)
                              (implicit project: Project, editor: Editor): Unit = {
    invokeOnSelected(ScalaBundle.message("choose.type.element.for", refactoringName))(invokesNext(selectedElement), invokesNext) {
      getTypeElements(selectedElement)
    }
  }

  private def invokeOnSelected[T <: ScalaPsiElement](@Nls message: String)
                                                    (default: => Unit, consumer: T => Unit)
                                                    (elements: => Seq[T])
                                                    (implicit project: Project, editor: Editor): Unit = {
    implicit val selectionModel: SelectionModel = editor.getSelectionModel

    if (selectionModel.hasSelection) default
    else {
      def onElement(element: T): Unit = {
        val textRange = element.getTextRange
        selectionModel.setSelection(textRange.getStartOffset, textRange.getEndOffset)

        consumer(element)
      }

      elements match {
        case Seq() =>
          selectionModel.selectLineAtCaret()
          default
        case Seq(element) => onElement(element)
        case elements => showPsiChooser(elements, onElement, message, getShortText)
      }
    }
  }

  def fileEncloser(file: PsiFile, startOffset: Int): Option[PsiElement] = {
    val elementAtOffset = Option(file.findElementAt(startOffset))
    val parentContainer = elementAtOffset.safeMap(PsiTreeUtil.getParentOfType(_, classOf[ScExtendsBlock], classOf[PsiFile]))
    parentContainer.orElse {
      file.getChildren.find(_.getTextRange.contains(startOffset))
    }
  }

  @TestOnly
  def enableInplaceRefactoringInTests(editor: Editor): Unit = {
   editor.putUserData(ENABLE_INPLACE_REFACTORING_IN_TESTS, java.lang.Boolean.TRUE)
  }

  private val ENABLE_INPLACE_REFACTORING_IN_TESTS: Key[lang.Boolean] =
    Key.create[java.lang.Boolean]("ENABLE_INPLACE_REFACTORING_IN_TESTS")

  def isInplaceAvailable(editor: Editor): Boolean =
    editor.getSettings.isVariableInplaceRenameEnabled && {
      if (ApplicationManager.getApplication.isUnitTestMode)
        editor.getUserData(ENABLE_INPLACE_REFACTORING_IN_TESTS)
      else
        true
    }

  def enclosingContainer(parent: PsiElement): PsiElement =
    Option(parent)
      .map(elem => elem.firstChild.getOrElse(elem)) //to make enclosing container non-strict
      //Even if a file can have multiple declarations with same name (like REPL worksheets)
      //we want to use a unique name when introducing variables (see SCL-18151)
      .flatMap(_.scopes(includeFilesWithAllowedDefinitionNameCollisions = true).to(LazyList).headOption)
      .orNull

  def commonParent(file: PsiFile, textRange: TextRange): PsiElement =
    commonParent(file, Seq(textRange))

  def commonParent(file: PsiFile, textRanges: Seq[TextRange]): PsiElement = {
    val offsets = textRanges.map(_.getStartOffset) ++ textRanges.map(_.getEndOffset - 1)
    PsiTreeUtil.findCommonParent(offsets.map(file.findElementAt).asJava)
  }

  def showErrorHintWithException(
    @Nls message: String,
    @Nls refactoringName: String,
    helpId: String = null
  )(implicit project: Project, editor: Editor): Nothing = {
    showErrorHint(message, refactoringName, helpId)
    throw new IntroduceException
  }

  def showErrorHint(
    @Nls message: String,
    @Nls refactoringName: String,
    helpId: String = null
  )(implicit project: Project, editor: Editor): Unit = {
    CommonRefactoringUtil.showErrorHint(project, editor, message, refactoringName, helpId)
  }

  private[refactoring]
  def writableScalaFile(file: PsiFile, @Nls refactoringName: String)
                       (implicit project: Project, editor: Editor): ScalaFile =
    file match {
      case scalaFile: ScalaFile if ensureFileWritable(file) =>
        scalaFile
      case _: ScalaFile =>
        showErrorHintWithException(ScalaBundle.message("file.is.not.writable"), refactoringName)
      case _ =>
        file.findAnyScalaFile.filter(ensureFileWritable).getOrElse {
          showErrorHintWithException(ScalaBundle.message("only.for.scala"), refactoringName)
        }
    }

  private[refactoring]
  def maybeWritableScalaFile(file: PsiFile, @Nls refactoringName: String)
                            (implicit project: Project, editor: Editor): Option[ScalaFile] =
    file match {
      case scalaFile: ScalaFile if ensureFileWritable(file) =>
        Some(scalaFile)
      case _: ScalaFile =>
        showErrorHint(ScalaBundle.message("file.is.not.writable"), refactoringName)
        None
      case _ =>
        val scalaFile = file.findAnyScalaFile
        scalaFile.filter(ensureFileWritable).orElse(None)
    }

  def canBeIntroduced(expr: ScExpression): Boolean = cannotBeIntroducedReason(expr).isEmpty

  // TODO: for some reason "reason" text is not shown in the error tooltip, e.g. when extracting variable
  // TODO: separate "reason" UI localized representation (create some enum/ADT for the reason)
  def cannotBeIntroducedReason(expr: ScExpression): Option[String] = {
    val exists1 = expr.parentOfType(classOf[ScSelfInvocation], strict = false)
      .flatMap(_.args)
      .exists(_.isAncestorOf(expr))

    if (exists1) {
      return Some(ScalaBundle.message("cannot.refactor.arg.in.self.invocation.of.constructor"))
    }

    val exists2 = expr.parentOfType(classOf[ScGuard], strict = false)
      .exists(_.getParent.is[ScCaseClause])
    if (exists2) {
      return Some(ScalaBundle.message("refactoring.is.not.supported.in.guard"))
    }

    val byExpression = expr match {
      case _: ScConstrBlockExpr =>
        ScalaBundle.message("cannot.refactor.constr.expression")
      case _: ScSelfInvocation =>
        ScalaBundle.message("cannot.refactor.self.invocation")
      case block: ScBlock if !block.isEnclosedByBraces =>
        ScalaBundle.message("cannot.refactor.not.expression")
      case (_: ScReferenceExpression) childOf (a: ScAssignment) if a.leftExpression == expr =>
        ScalaBundle.message("cannot.refactor.named.arg")
      case (_: ScAssignment) childOf (_: ScArgumentExprList)  =>
        ScalaBundle.message("cannot.refactor.named.arg")
      case ElementType(ScalaElementType.INTERPOLATED_PREFIX_LITERAL_REFERENCE) =>
        ScalaBundle.message("cannot.refactor.interpolated.string.prefix")
      case _ =>
        null
    }

    val parent = expr.getParent
    val byParent = parent match {
      case ScInfixExpr(_, operation, _) if operation == expr => ScalaBundle.message("cannot.refactor.not.expression")
      case ScPostfixExpr(_, operation) if operation == expr => ScalaBundle.message("cannot.refactor.not.expression")
      case _: ScGenericCall => ScalaBundle.message("cannot.refactor.under.generic.call")
      case _: ScLiteralPattern => ScalaBundle.message("cannot.refactor.literal.pattern")
      case par: ScClassParameter =>
        par.containingClass match {
          case clazz: ScClass if clazz.isTopLevel => ScalaBundle.message("cannot.refactor.class.parameter.top.level")
          case _ => null
        }
      case _ => null
    }
    Option(byExpression).orElse(Option(byParent))
  }

  private def replaceOccurrence(textRange: TextRange, newString: String, file: PsiFile): RangeMarker = {
    val documentManager = PsiDocumentManager.getInstance(file.getProject)
    val document = documentManager.getDocument(file)
    var startShift = 0
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
        startShift = pars.getTextRange.getStartOffset - inner.getTextRange.getStartOffset + (if (afterWord) 1 else 0)
        document.replaceString(textRange.getStartOffset, textRange.getEndOffset, (if (afterWord) " " else "") + newString)
      case ChildOf(ScPostfixExpr(_, `parent`)) =>
        //This case for block argument expression
        val textRange = parent.getTextRange
        document.replaceString(textRange.getStartOffset, textRange.getEndOffset, "(" + newString + ")")
        startShift = 1
      case _: ScReferencePattern =>
        val textRange = parent.getTextRange
        document.replaceString(textRange.getStartOffset, textRange.getEndOffset, "`" + newString + "`")
      case literal: ScStringLiteral =>
        val prefix = literal match {
          case interpolatedString: ScInterpolatedStringLiteral => interpolatedString.referenceName
          case _ => ""
        }
        val replaceAsInjection = Seq("s", "raw").contains(prefix)

        if (replaceAsInjection) {
          val nextChar = file.charSequence.charAt(newRange.getEndOffset)
          val needBraces = needBracesForInjection(newString, nextChar)
          val text = if (needBraces) s"$${$newString}" else s"$$$newString"
          startShift = if (needBraces) 2 else 1
          document.replaceString(newRange.getStartOffset, newRange.getEndOffset, text)
        } else {
          val quote = if (literal.isMultiLineString) "\"\"\"" else "\""
          val contentRange = literal.contentRange
          val isStart = newRange.getStartOffset == contentRange.getStartOffset
          val isEnd = newRange.getEndOffset == contentRange.getEndOffset
          val firstPart = if (!isStart) s"$quote + " else ""
          val lastPart = if (!isEnd) s" + $prefix$quote" else ""
          val text = s"$firstPart$newString$lastPart"
          val literalRange = literal.getTextRange
          val startOffset = if (isStart) literalRange.getStartOffset else newRange.getStartOffset
          val endOffset = if (isEnd) literalRange.getEndOffset else newRange.getEndOffset
          document.replaceString(startOffset, endOffset, text)
          startShift = if (isStart) startOffset - newRange.getStartOffset else firstPart.length
        }
      case (_: ScReferenceExpression) childOf ((block: ScBlock) childOf (_: ScInterpolatedStringLiteral)) =>
        val nextChar = file.charSequence.charAt(block.endOffset)
        val needBraces = needBracesForInjection(newString, nextChar)
        if (!needBraces) {
          document.replaceString(block.startOffset, block.endOffset, newString)
          startShift = -1
        }
      case _ =>
    }
    documentManager.commitDocument(document)
    val newStart = start + startShift
    val newEnd = newStart + newString.length
    val newExpr = PsiTreeUtil.findElementOfClassAtRange(file, newStart, newEnd, classOf[ScExpression])
    val newPattern = PsiTreeUtil.findElementOfClassAtOffset(file, newStart, classOf[ScPattern], true)
    val rangeMarker = Option(newExpr).orElse(Option(newPattern))
      .map(elem => document.createRangeMarker(elem.getTextRange))
      .getOrElse(throw new IntroduceException)
    rangeMarker
  }

  private def needBracesForInjection(refText: String, nextChar: Char): Boolean = {
    nextChar != '$' && nextChar != '"' && isIdentifier(refText + nextChar)
  }

  def replaceOccurrences(occurrences: Seq[TextRange], newString: String, file: PsiFile): Seq[TextRange] = {
    val reversedRangeMarkers = occurrences.reverseIterator.map(replaceOccurrence(_, newString, file)).to(Seq)
    reversedRangeMarkers.reverseIterator.map(rm => new TextRange(rm.getStartOffset, rm.getEndOffset)).to(Seq)
  }

  def statementsAndMembersInClass(aClass: ScTemplateDefinition): Seq[PsiElement] = {
    val extendsBlock = aClass.extendsBlock
    if (extendsBlock == null) return Nil
    val body = extendsBlock.templateBody
    val earlyDefs = extendsBlock.earlyDefinitions
    (earlyDefs ++ body)
      .flatMap(_.children)
      .filter(child => child.is[ScBlockStatement, ScMember])
      .toSeq
  }

  @tailrec
  def findParentExpr(elem: PsiElement): ScExpression = {
    def checkEnd(nextParent: PsiElement, parExpr: ScExpression): Boolean = {
      if (parExpr.is[ScBlock]) return true
      val result: Boolean = nextParent match {
        case _: ScBlock => true
        case forSt: ScFor if forSt.body.orNull == parExpr => false //in this case needBraces == true
        case _: ScFor => true
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
      case _: ScBlock | _: ScTemplateBody | _: ScEarlyDefinitions | _: ScalaFile | _: ScCaseClause => false
      case _: ScFunction => true
      case (_: ScFunction) & (_ childOf (_: ScTemplateBody | _: ScEarlyDefinitions)) => true
      case ifSt: ScIf if Seq(ifSt.thenExpression, ifSt.elseExpression) contains Option(parExpr) => true
      case forSt: ScFor if forSt.body.orNull == parExpr => true
      case _: ScFor => false
      case _: ScForBinding | _: ScGenerator => false
      case guard: ScGuard if guard.getParent.is[ScEnumerators] => false
      case whSt: ScWhile if whSt.expression.orNull == parExpr => true
      case doSt: ScDo if doSt.body.orNull == parExpr => true
      case tryExpr: ScTry if tryExpr.expression.orNull == parExpr => true
      case finBl: ScFinallyBlock if finBl.expression.orNull == parExpr => true
      case fE: ScFunctionExpr =>
        fE.getContext match {
          case be: ScBlock if be.resultExpression.contains(fE) => false
          case _ => true
        }
      case _ => false
    }
  }

  def checkForwardReferences(expr: ScExpression, position: PsiElement): Boolean = {
    var result = true
    val visitor = new ScalaRecursiveElementVisitor() {
      override def visitReferenceExpression(ref: ScReferenceExpression): Unit = {
        ref.getParent match {
          case ScInfixExpr(_, `ref`, _) =>
          case ScPostfixExpr(_, `ref`) =>
          case ScPrefixExpr(`ref`, _) =>
          case _ =>
            val newRef = createExpressionWithContextFromText(ref.getText, position).asInstanceOf[ScReferenceExpression]
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

  private[refactoring]
  def showNotPossibleWarnings(elements: Seq[PsiElement], @Nls refactoringName: String)
                             (implicit project: Project, editor: Editor): Boolean = {
    def errors(elem: PsiElement): Option[String] = elem match {
      case funDef: ScFunctionDefinition if hasOutsideUsages(funDef) => ScalaBundle.message("cannot.extract.used.function.definition").toOption
      case _: ScBlockStatement => None
      case comm: PsiComment if !comm.getParent.is[ScMember] => None
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
        .forall(referenced => elements.exists(PsiTreeUtil.isAncestor(_, referenced, false)))

    val messages = elements.flatMap(errors).distinct
    if (messages.nonEmpty) {
      //noinspection ReferencePassedToNls
      showErrorHint(messages.mkString("\n"), refactoringName)
      return true
    }

    if (elements.isEmpty || !elements.exists(_.is[ScBlockStatement])) {
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
