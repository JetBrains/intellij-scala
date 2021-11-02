package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder, LookupElementPresentation}
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.{ElementPattern, PatternCondition}
import com.intellij.psi.impl.source.DummyHolder
import com.intellij.psi.util.PsiTreeUtil.{getContextOfType, getParentOfType}
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import com.intellij.util.ProcessingContext
import com.intellij.util.ui.EmptyIcon
import org.jetbrains.plugins.scala.editor.enterHandler.EnterHandlerUtils.calcCaretIndent
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaEndMarkerCompletionContributor._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScEnd
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

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

  private def extendBasicAndSmart(place: ElementPattern[_ <: PsiElement],
                                  provider: EndMarkerCompletionProvider[_ <: ScalaPsiElement]): Unit = {
    extend(CompletionType.BASIC, place, provider)
    extend(CompletionType.SMART, place, provider)
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
      markerCtx <- getMarkerContext(position).toIterable
      // collect possible end marker suggestions for all parents and one previous sibling
      // use index to determine lookup element priority: show closest first
      (element, idx) <- (markerCtx.prevSiblingNotWhitespaceComment ++ markerCtx.contexts).zipWithIndex
      token <- findEndMarkerToken(element, markerCtx)
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
      val constructor = templateParents.constructorInvocation.fold("Object")(_.typeElement.getText)
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
  private def isPossibleEndMarkerOwner(place: PsiElement, contextElement: PsiElement): Boolean = {
    @tailrec
    def check(element: PsiElement): Boolean =
      if (element == null) false
      else contextElement match {
        case PrevSiblingNotWhitespaceComment(`element`) => true
        case Parent(`element`) => element.getLastChild == contextElement
        case _ => check(element.getLastChild)
      }

    check(place)
  }

  private def isMultilineAndNonEmpty(tb: ScTemplateBody): Boolean =
    isMultiline(tb) && (tb.exprs.nonEmpty || tb.members.nonEmpty || tb.holders.nonEmpty)

  /**
   * Return specifier token metadata for the possible end marker of given element
   *
   * @param place          element that might have an end marker
   * @param contextElement element corresponding to the end marker start
   * @see [[https://docs.scala-lang.org/scala3/reference/other-new-features/indentation.html#the-end-marker Scala 3 reference]]
   */
  private def findEndMarkerToken(place: PsiElement, contextElement: PsiElement): Option[EndMarkerToken] =
    if (!isPossibleEndMarkerOwner(place, contextElement) || !isFirstNonWhitespaceChildInLine(place)) None
    else place match {
      // anonymous class
      case ntd: ScNewTemplateDefinition if ntd.extendsBlock.isAnonymousClass =>
        val templateBody = ntd.extendsBlock.templateBody
        Option.when(templateBody.exists(isMultilineAndNonEmpty)) {
          EndMarkerToken.keyword(ScalaTokenType.NewKeyword.keywordText, templateParents = ntd.extendsBlock.templateParents)
        }

      // class, trait, object, enum
      case td: ScTypeDefinition if td.is[ScClass, ScTrait, ScObject, ScEnum] =>
        val templateBody = td.extendsBlock.templateBody
        Some(EndMarkerToken.identifier(td.name))
          .filter(_ => templateBody.exists(isMultilineAndNonEmpty) || td.extendsBlock.cases.nonEmpty)

      // val/var v = ???
      case SimpleValOrVarDefinitionWithMultilineBody(binding) =>
        Some(EndMarkerToken.identifier(binding.name))

      // given
      case GivenWithMultilineBody(scGiven) =>
        scGiven.nameElement match {
          case Some(nameElement) => Some(EndMarkerToken.identifier(nameElement.getText))
          case None => Some(EndMarkerToken.keyword(ScalaTokenType.GivenKeyword.keywordText))
        }

      // extension
      case ext: ScExtension if ext.extensionMethods.nonEmpty && ext.extensionBody.exists(isMultiline) =>
        Some(EndMarkerToken.keyword(ScalaTokenType.ExtensionKeyword.keywordText))

      // def d = ???
      // constructor
      case fn: ScFunctionDefinition if fn.body.exists(isMultiline) =>
        val data =
          if (fn.isConstructor) EndMarkerToken.keyword(fn.name)
          else EndMarkerToken.identifier(fn.name)
        Some(data)

      // val definition binding pattern
      case pd: ScPatternDefinition if !pd.isSimple && pd.expr.exists(isMultiline) =>
        Some(EndMarkerToken.keyword(pd.keywordToken.getNode.getElementType.toString))

      // if, while, for, try, match
      case scIf: ScIf if scIf.thenExpression.exists(isMultiline) || scIf.elseExpression.exists(isMultiline) =>
        Some(EndMarkerToken.keyword(ScalaTokenTypes.kIF.toString))
      case scWhile: ScWhile if scWhile.expression.exists(isMultiline) =>
        Some(EndMarkerToken.keyword(ScalaTokenTypes.kWHILE.toString))
      case scFor: ScFor if scFor.body.exists(isMultiline) =>
        Some(EndMarkerToken.keyword(ScalaTokenTypes.kFOR.toString))
      case scTry: ScTry if scTry.expression.exists(isMultiline) ||
        scTry.catchBlock.flatMap(_.caseClauses).exists(isMultiline) ||
        scTry.finallyBlock.flatMap(_.expression).exists(isMultiline) =>
        Some(EndMarkerToken.keyword(ScalaTokenTypes.kTRY.toString))
      case scMatch: ScMatch if scMatch.caseClauses.exists(isMultiline) =>
        Some(EndMarkerToken.keyword(ScalaTokenTypes.kMATCH.toString))

      // package (package p1.p2:)
      case p: ScPackaging if p.isExplicit =>
        p.reference.map(r => EndMarkerToken.identifier(r.refName))

      case _ => None
    }

  private object SimpleValOrVarDefinitionWithMultilineBody {
    def unapply(valOrVar: ScValueOrVariableDefinition): Option[ScBindingPattern] = valOrVar.bindings
      .headOption
      .filter(binding => valOrVar.isSimple && !binding.isWildcard && valOrVar.expr.exists(isMultiline))
  }

  private object GivenWithMultilineBody {
    def unapply(scGiven: ScGiven): Option[ScGiven] = scGiven match {
      case alias: ScGivenAlias =>
        Option.when(alias.hasAssign && alias.body.exists(isMultiline))(alias)
      case definition: ScGivenDefinition =>
        Option(definition).filter(_.extendsBlock.templateBody.exists(isMultilineAndNonEmpty))
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
          if (token.isKeyword) {
            presentation.setItemText(endMarker)
          } else {
            presentation.setItemText(ScalaKeyword.END)
            presentation.setTailText(" ")
            presentation.appendTailText(token.specifierToken, false)
          }
          presentation.setItemTextBold(true)
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

            (calcCaretIndent(contextOffset, documentText, tabSize), calcCaretIndent(targetOffset, documentText, tabSize)) match {
              case (Some(contextIndent), Some(targetIndent)) if contextIndent > targetIndent =>
                document.deleteString(contextOffset - (contextIndent - targetIndent), contextOffset)
              case _ =>
            }
          case _ =>
        }
    }
  }
}
