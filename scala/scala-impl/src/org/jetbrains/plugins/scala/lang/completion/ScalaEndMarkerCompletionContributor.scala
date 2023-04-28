package org.jetbrains.plugins.scala.lang.completion

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder, LookupElementPresentation}
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.{ElementPattern, PatternCondition, PsiElementPattern}
import com.intellij.psi.impl.source.DummyHolder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil.{getContextOfType, getParentOfType}
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import com.intellij.util.ProcessingContext
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.plugins.scala.editor.enterHandler.EnterHandlerUtils.calcCaretIndent
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaEndMarkerCompletionContributor._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScEnd, ScOptionalBracesOwner}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.{ScBegin, ScalaPsiElement}

import scala.annotation.tailrec

final class ScalaEndMarkerCompletionContributor extends CompletionContributor {

  extendBasicAndSmart(identifierWithParentPattern(classOf[ScEnd]),
    new EndMarkerCompletionProvider(classOf[ScEnd]))

  extendBasicAndSmart(scala3IdentifierWithParentPattern(referenceAfterEndPattern(classOf[ScPostfixExpr])),
    new EndMarkerCompletionProvider(classOf[ScPostfixExpr]))

  extendBasicAndSmart(scala3IdentifierWithParentPattern(referenceAfterEndPattern(classOf[ScInfixExpr])),
    new EndMarkerCompletionProvider(classOf[ScInfixExpr]))

  extendBasicAndSmart(scala3IdentifierWithParentPattern(psiElement(classOf[ScReferenceExpression])
    .`with`(firstNonWhitespaceChildInLinePattern)),
    new EndMarkerCompletionProvider(classOf[ScReferenceExpression], useEndKeywordInLookupString = true))

  private def extendBasicAndSmart(place: PsiElementPattern.Capture[_ <: PsiElement],
                                  provider: EndMarkerCompletionProvider[_ <: ScalaPsiElement]): Unit = {
    val pattern = place.notAfterLeafSkippingWhitespaceComment(ScalaTokenTypes.tDOT)

    extend(CompletionType.BASIC, pattern, provider)
    extend(CompletionType.SMART, pattern, provider)
  }
}

object ScalaEndMarkerCompletionContributor {
  private def scala3IdentifierWithParentPattern[T <: PsiElement](parentPattern: ElementPattern[T]) =
    identifierPattern
      .isInScala3File
      .withParent(parentPattern)

  private def isMultiline(element: PsiElement): Boolean = element.textContains('\n')

  private val whitespaceWithoutLineBreaksPattern =
    psiElement.`with`(
      new PatternCondition[PsiElement]("whitespaceWithoutLineBreaksPattern") {
        override def accepts(element: PsiElement, context: ProcessingContext): Boolean =
          element.is[PsiWhiteSpace] && !isMultiline(element) ||
            element.getNode != null && element.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE
      }
    )

  private val misalignedEndReferencePattern =
    psiElement(classOf[ScReferenceExpression]).withText(ScalaKeyword.END)

  private def referenceAfterEndPattern[T <: PsiElement](parentClass: Class[T]) =
    psiElement(classOf[ScReferenceExpression])
      .withParent(parentClass)
      .afterSiblingSkipping(
        whitespaceWithoutLineBreaksPattern,
        misalignedEndReferencePattern
      )

  private val firstNonWhitespaceChildInLinePattern =
    new PatternCondition[PsiElement]("firstNonWhitespaceChildInLinePattern") {
      override def accepts(element: PsiElement, context: ProcessingContext): Boolean =
        element.prevSiblings
          .dropWhile(whitespaceWithoutLineBreaksPattern.accepts)
          .nextOption()
          .forall(_.is[PsiWhiteSpace]) // accept if none or whitespace with line break
    }

  private def isFirstNonWhitespaceChildInLine(element: PsiElement): Boolean =
    firstNonWhitespaceChildInLinePattern.accepts(element, new ProcessingContext())

  /**
   * @param endMarkerContextClass       class of the ancestor for the current position that contains end marker start
   * @param useEndKeywordInLookupString if false then the end keyword is already present in the source otherwise suggest end + specifier token
   */
  private class EndMarkerCompletionProvider[T <: ScalaPsiElement](endMarkerContextClass: Class[T],
                                                                  useEndKeywordInLookupString: Boolean = false) extends ScalaCompletionProvider {
    override protected def completionsFor(position: PsiElement)
                                         (implicit parameters: CompletionParameters,
                                          context: ProcessingContext): Iterable[LookupElement] = for {
      markerCtx <- Iterable.from(getMarkerContext(position))
      // collect possible end marker suggestions for all parents and one previous sibling
      // use index to determine lookup element priority: show closest first
      (element, idx) <- (markerCtx.prevSiblingNotWhitespaceComment ++ markerCtx.contexts).zipWithIndex
      token <- findEndMarkerToken(element)(markerCtx)
    } yield EndMarkerLookupItem(token, getMarkerContextOffset(markerCtx), element, -idx, useEndKeywordInLookupString)

    /**
     * Find an element containing end marker start. If position is wrapped into DummyHolder at some point then
     * text offsets for indent calculation could be wrong. In this case use position from orignal file.
     *
     * If original position is a whitespace (invoked completion without typing)
     * then try to find end-like element and return this whitespace if not found
     *
     * @param position element under caret in the completion file
     */
    private def getMarkerContext(position: PsiElement)(implicit params: CompletionParameters): Option[PsiElement] =
      if (getParentOfType(position, classOf[DummyHolder]) == null)
        getContextOfType(position, endMarkerContextClass).toOption
      else {
        val originalPosition = params.getOriginalPosition
        if (originalPosition.is[PsiWhiteSpace]) {
          originalPosition.prevSiblingNotWhitespace
            .collect {
              case Parent(end: ScEnd) => end
              case sibling if misalignedEndReferencePattern.accepts(sibling) => sibling
            }
            .orElse(Some(originalPosition))
        } else getContextOfType(originalPosition, endMarkerContextClass).toOption
      }

    /**
     * Return possible end marker start offset
     *
     * @param markerCtx element containing end marker start or a whitespace
     * @return markerCtx start offset if it is not a whitespace otherwise - completion params offset
     */
    private def getMarkerContextOffset(markerCtx: PsiElement)(implicit parameters: CompletionParameters): Int =
      if (markerCtx.is[PsiWhiteSpace]) parameters.getOffset else markerCtx.startOffset
  }

  /**
   * @param specifierToken identifier or one of the following keywords: if, while, for, match, try, new, this, val, given
   * @param typeHint       hint to be shown in the lookup element typeText. e.g.: class name for new keyword
   */
  private final case class EndMarkerToken(specifierToken: String, isKeyword: Boolean, typeHint: Option[String])
  private object EndMarkerToken {
    def identifier(specifierToken: String): EndMarkerToken =
      new EndMarkerToken(specifierToken, isKeyword = false, typeHint = None)

    def keyword(specifierToken: String, templateParents: Option[ScTemplateParents] = None): EndMarkerToken =
      new EndMarkerToken(specifierToken, isKeyword = true, typeHint = templateParents.map(getTemplateParentsShortText))

    private def getTemplateParentsShortText(templateParents: ScTemplateParents): String = {
      val constructor = templateParents.firstParentClause.fold("Object")(_.typeElement.getText)
      val hasMoreTypes = templateParents.allTypeElements.length > 1

      if (hasMoreTypes) s"$constructor with ..." else constructor
    }
  }

  /**
   * Check if an end marker should be suggested
   *
   * @param place          expression that might be an end marker owner
   * @param contextElement end marker start
   * @return true if <code>contextElement</code> is at the end of the <code>place</code>'s scope
   *         or is it's next sibling (skipping whitespaces and comments)
   */
  private def isPossibleEndMarkerOwner(place: PsiElement)(implicit contextElement: PsiElement): Boolean = {
    @tailrec
    def check(element: PsiElement): Boolean =
      if (element == null) false
      else contextElement match {
        case PrevSiblingNotWhitespaceComment(`element`) => true
        case Parent(`element`) => element.getLastChild == contextElement
        case _ => check(element.getLastChild)
      }

    place match {
      case begin: ScBegin if begin.end.forall(_ == contextElement) =>
        check(begin)
      case _ => false
    }
  }

  private def isWithoutBraces(element: ScalaPsiElement): Boolean = element match {
    case braceOwner: ScOptionalBracesOwner => !braceOwner.isEnclosedByBraces
    case _ => true
  }

  private def isMultilineWithoutBraces(element: ScalaPsiElement): Boolean =
    isMultiline(element) && isWithoutBraces(element)

  /**
   * Return specifier token metadata for the possible end marker of given element
   *
   * @param place          element that might have an end marker
   * @param contextElement element corresponding to the end marker start
   * @see [[https://docs.scala-lang.org/scala3/reference/other-new-features/indentation.html#the-end-marker Scala 3 reference]]
   */
  private def findEndMarkerToken(place: PsiElement)(implicit contextElement: PsiElement): Option[EndMarkerToken] =
    if (!isPossibleEndMarkerOwner(place) || !isFirstNonWhitespaceChildInLine(place)) None
    else place match {
      // anonymous class
      case ntd: ScNewTemplateDefinition if ntd.extendsBlock.isAnonymousClass =>
        val templateBody = ntd.extendsBlock.templateBody
        Option.when(templateBody.exists(isWithoutBraces)) {
          EndMarkerToken.keyword(ScalaTokenType.NewKeyword.keywordText, templateParents = ntd.extendsBlock.templateParents)
        }

      // class, trait, object, enum
      case td: ScTypeDefinition if td.is[ScClass, ScTrait, ScObject, ScEnum] =>
        val templateBody = td.extendsBlock.templateBody
        Option.when(templateBody.exists(isWithoutBraces)) {
          EndMarkerToken.identifier(td.name)
        }

      // val/var v = ???
      case SimpleValOrVarDefinitionWithMultilineBody(binding) =>
        Some(EndMarkerToken.identifier(binding.name))

      // given
      case GivenWithMultilineBody(scGiven, _) =>
        scGiven.nameElement match {
          case Some(nameElement) => Some(EndMarkerToken.identifier(nameElement.getText))
          case None => Some(EndMarkerToken.keyword(ScalaTokenType.GivenKeyword.keywordText))
        }

      // extension
      case ext: ScExtension if ext.extensionMethods.nonEmpty && ext.extensionBody.exists(isMultiline) =>
        val hasBraces = ext.extensionBody.flatMap(_.firstChild).exists(_.elementType == ScalaTokenTypes.tLBRACE)
        Option.when(!hasBraces)(EndMarkerToken.keyword(ScalaTokenType.ExtensionKeyword.keywordText))

      // def d = ???
      // constructor
      case fn: ScFunctionDefinition if fn.body.exists(isMultilineWithoutBraces) =>
        val data =
          if (fn.isConstructor) EndMarkerToken.keyword(fn.name)
          else EndMarkerToken.identifier(fn.name)
        Some(data)

      // val definition binding pattern
      case pd: ScPatternDefinition if !pd.isSimple && pd.expr.exists(isMultilineWithoutBraces) =>
        Some(EndMarkerToken.keyword(pd.keywordToken.getNode.getElementType.toString))

      // if, while, for, try, match
      case ControlExpr(keyword) =>
        Some(EndMarkerToken.keyword(keyword.toString))

      // package (package p1.p2:)
      case p: ScPackaging if p.isExplicit =>
        p.reference.map(r => EndMarkerToken.identifier(r.refName))

      case _ => None
    }

  private object ControlExpr {
    /**
     * @return keyword type (if, while, try, for, match) if given expression has multiline braceless part
     *         and the last part doesn't have braces */
    def unapply(expr: ScExpression): Option[IElementType] = expr match {
      case scIf: ScIf with ScBegin =>
        keywordTypeIfAccepted(scIf)(scIf.thenExpression, scIf.elseExpression)
      case scWhile: ScWhile with ScBegin =>
        keywordTypeIfAccepted(scWhile)(scWhile.expression)
      case scTry: ScTry with ScBegin =>
        val expression = scTry.expression
        val catchExpr = scTry.catchBlock.flatMap(_.expression)
        val catchCaseClauses = scTry.catchBlock.flatMap(_.caseClauses)
        val finallyExpr = scTry.finallyBlock.flatMap(_.expression)

        keywordTypeIfAccepted(scTry)(expression, catchExpr, catchCaseClauses, finallyExpr)
      case scFor: ScFor with ScBegin =>
        val cond = scFor.body match {
          case Some(body) if isWithoutBraces(body) =>
            isMultiline(body) || (scFor.enumerators.exists(isMultiline) && scFor.getLeftBracket.isEmpty)
          case _ => false
        }

        Option.when(cond)(scFor.keyword.elementType)
      case scMatch: ScMatch with ScBegin =>
        val cond = scMatch.caseClauses.exists(clauses => isMultiline(clauses) &&
          !clauses.prevSiblingNotWhitespaceComment.exists(_.elementType == ScalaTokenTypes.tLBRACE))

        Option.when(cond)(scMatch.keyword.elementType)
      case _ => None
    }

    /**
     * @param begin expression (if/while/try)
     * @param elems `begin` expression parts (then/else expr, catch case clauses, etc.)
     * @return keyword element type if last element doesn't have braces and there is at least one element
     *         that is multiline and without braces
     */
    private def keywordTypeIfAccepted(begin: ScBegin)(elems: Option[ScalaPsiElement]*): Option[IElementType] = {
      val elements = elems.flatten
      lazy val cond = elements.exists(isMultilineWithoutBraces) && elements.lastOption.exists(isWithoutBraces)

      Option.when(cond)(begin.keyword.elementType)
    }
  }

  private object SimpleValOrVarDefinitionWithMultilineBody {
    def unapply(valOrVar: ScValueOrVariableDefinition): Option[ScBindingPattern] = valOrVar.bindings
      .headOption
      .filter(binding => valOrVar.isSimple && !binding.isWildcard && valOrVar.expr.exists(isMultilineWithoutBraces))
  }

  private object GivenWithMultilineBody {
    def unapply(scGiven: ScGiven): Option[(ScGiven, Option[ScTemplateBody])] = scGiven match {
      case alias: ScGivenAliasDefinition =>
        alias.body.collect {
          case body if alias.hasAssign && isMultilineWithoutBraces(body) =>
            (alias, None)
        }
      case definition: ScGivenDefinition =>
        definition.extendsBlock.templateBody.collect {
          case body if isMultilineWithoutBraces(body) && (body.exprs.nonEmpty || body.members.nonEmpty || body.holders.nonEmpty) =>
            (definition, Some(body))
        }
      case _ => None
    }
  }

  private object EndMarkerLookupItem {
    private final case class OffsetData(contextOffset: Int, targetOffset: Int)

    /**
     * @param token                       end marker specifier token metadata
     * @param contextOffset               end marker start offset in the source file
     * @param target                      target element that should be ended with a marker
     * @param priority                    value used for lookup elements sorting (higher priority puts the item closer to the beginning of the list)
     * @param useEndKeywordInLookupString if true then the lookup string will be prefixed with "end "
     * @return prioritized lookup element for an end marker
     */
    def apply(token: EndMarkerToken, contextOffset: Int, target: PsiElement,
              priority: Int, useEndKeywordInLookupString: Boolean): LookupElement = {
      val endMarker = s"${ScalaKeyword.END} ${token.specifierToken}"
      val offsetData = OffsetData(contextOffset, target.startOffset)
      val lookupString = if (useEndKeywordInLookupString) endMarker else token.specifierToken

      val builder = LookupElementBuilder.create(offsetData, lookupString)
        .withIcon(EmptyIcon.ICON_16)
        .withInsertHandler(new EndMarkerInsertHandler)
        .withRenderer { (_, presentation: LookupElementPresentation) =>
          if (useEndKeywordInLookupString) {
            if (token.isKeyword) presentation.setItemText(endMarker)
            else {
              presentation.setItemText(ScalaKeyword.END)
              presentation.setTailText(" ")
              presentation.appendTailText(token.specifierToken, false)
            }
          } else presentation.setItemText(lookupString)

          presentation.setItemTextBold(useEndKeywordInLookupString || token.isKeyword)
          token.typeHint.foreach(presentation.setTypeText)
        }

      PrioritizedLookupElement.withPriority(builder, priority)
    }

    private final class EndMarkerInsertHandler extends InsertHandler[LookupElement] {
      override def handleInsert(insertionContext: InsertionContext, item: LookupElement): Unit =
        item.getObject match {
          case OffsetData(contextOffset, targetOffset) =>
            insertionContext.commitDocument()

            val document = insertionContext.getDocument
            val file = insertionContext.getFile
            val tabSize = CodeStyle.getIndentOptions(file).TAB_SIZE
            val documentText = document.getCharsSequence
            val maybeTargetIndent = calcCaretIndent(targetOffset, documentText, tabSize)

            (calcCaretIndent(contextOffset, documentText, tabSize), maybeTargetIndent) match {
              case (Some(contextIndent), Some(targetIndent)) if contextIndent > targetIndent =>
                document.deleteString(contextOffset - (contextIndent - targetIndent), contextOffset)
              case _ =>
            }

            // insert new line followed by target indent and move caret
            val indent = maybeTargetIndent.getOrElse(0)
            document.insertString(insertionContext.getTailOffset, "\n" + (" " * indent))
            insertionContext.getEditor.getCaretModel.moveCaretRelatively(indent, 1, false, false, false)
          case _ =>
        }
    }
  }
}
