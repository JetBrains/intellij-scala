package org.jetbrains.plugins.scala.grazie

import com.intellij.grazie.text.TextContent.{Exclusion, TextDomain}
import com.intellij.grazie.text.{TextContent, TextContentBuilder, TextExtractor}
import com.intellij.grazie.utils.{HtmlUtilsKt, PsiUtilsKt}
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.injection.ScalaInjectionInfosCollector
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.{ScDocComment, ScDocInlinedTag, ScDocResolvableCodeReference, ScDocTag}

import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava

/**
 * @see java example [[com.intellij.grazie.ide.language.java.JavaTextExtractor]]
 */
final class ScalaTextExtractor extends TextExtractor:

  private val ExcludedScalaDocElementTypes: TokenSet = TokenSet.create(
    ScalaDocTokenType.DOC_COMMENT_START,
    ScalaDocTokenType.DOC_COMMENT_END,
    ScalaDocTokenType.DOC_COMMENT_LEADING_ASTERISKS,
    //Exception in `@throws Exception description`, myParam `@param myParam description`
    ScalaDocTokenType.DOC_TAG_VALUE_TOKEN,
    ScalaDocTokenType.DOC_INNER_CODE,
    ScalaDocTokenType.DOC_HTTP_LINK_VALUE,
  )

  /** @see [[com.intellij.grazie.text.TextContent.ExclusionKind.unknown]] */
  private val UnknownTokens = TokenSet.create(
    //link in `[[...]]`` (note, we still process description inside (e.g. `[[a.b.c description text]]`
    ScalaDocTokenType.DOC_LINK_TAG,
    ScalaDocTokenType.DOC_LINK_CLOSE_TAG,
    ScalaDocTokenType.DOC_HTTP_LINK_TAG,
    ScalaDocTokenType.DOC_LIST_ITEM_HEAD,
  )

  private val scaladocBuilder: TextContentBuilder =
    TextContentBuilder.FromPsi
      .withUnknown: e =>
        e.is[ScDocInlinedTag] || e.is[LeafPsiElement] && UnknownTokens.contains(PsiUtilCore.getElementType(e))
      .excluding: e =>
        val elementType = PsiUtilCore.getElementType(e)
        ExcludedScalaDocElementTypes.contains(elementType) ||
          e.is[LeafPsiElement] && ScalaDocTokenType.ALL_SCALADOC_SYNTAX_ELEMENTS.contains(elementType) ||
          e.is[ScDocResolvableCodeReference]
      .removingIndents(" \t")
      .removingLineSuffixes(" \t")

  private val scaladocBuilderWithoutTags = scaladocBuilder.excluding(_.is[ScDocTag])

  override def buildTextContents(root: PsiElement, allowedDomains: util.Set[TextContent.TextDomain]): util.List[TextContent] =
    // Handle ScalaDoc comments
    if (allowedDomains.contains(TextDomain.DOCUMENTATION))
      root match
        case _: ScDocComment =>
          return HtmlUtilsKt.excludeHtml(scaladocBuilderWithoutTags.build(root, TextDomain.DOCUMENTATION))
        case _: ScDocTag =>
          return HtmlUtilsKt.excludeHtml(scaladocBuilder.build(root, TextDomain.DOCUMENTATION))
        case _ =>

    // Handle line & block comments
    def isPlainCommentToken(e: PsiElement): Boolean =
      ScalaTokenTypes.PLAIN_COMMENTS_TOKEN_SET.contains(PsiUtilCore.getElementType(e))

    if (allowedDomains.contains(TextDomain.COMMENTS) && isPlainCommentToken(root))
      val roots = PsiUtilsKt.getNotSoDistantSimilarSiblings(root, e => isPlainCommentToken(e))
      return ContainerUtil.createMaybeSingletonList(
        TextContent.joinWithWhitespace('\n', ContainerUtil.mapNotNull(roots, (c: PsiElement) => {
          TextContentBuilder.FromPsi.removingIndents(" \t*/").removingLineSuffixes(" \t").build(c, TextDomain.COMMENTS)
        })))

    // Handle string literals
    if (allowedDomains.contains(TextDomain.LITERALS))
      root match
        case string: ScStringLiteral =>
          val content = TextContentBuilder.FromPsi.build(string, TextDomain.LITERALS)
          if (content != null) {
            // This offset might not be equal to content start in string literal. This is because content trims whitespaces
            val offsetFromLiteralStart = content.getRangesInFile.get(0).getStartOffset - string.getTextOffset
            val contentText = content.toString

            // Reusing existing logic from language injection.
            // We need to pass some language just because API requires it. It doesn't matter which language we pass
            val injectionInfos = ScalaInjectionInfosCollector.collectInjectionInfos(Seq(string), PlainTextLanguage.INSTANCE, "", "")
            val contentRangesInLiteral = injectionInfos.ranges
            val excludedRanges = contentRangesInLiteral.zip(contentRangesInLiteral.tail).flatMap { case (prev, next) =>
              // We need to use `max(0)` because `TextContentBuilder.build` returns content with spaces trimmed
              val start = (prev.range.getEndOffset - offsetFromLiteralStart).max(0)
              val end = (next.range.getStartOffset - offsetFromLiteralStart).max(0)

              // Multiline string literals without margin can empty ranges for every blank line
              // We need to filter such empty ranges because `Exclusion` constructor will fail otherwise
              if (start == end) None else {
                val isInterpolationInjectionExclusion = contentText.lift(start).contains('$')
                // Treat ${} injection as "Unknown" in order grammar check uses it as a border at which a new analyses should be started
                // In s"this is example" we can reliable run the check and detect missing article "an"
                // But in s"this is $text example" we can't do it because $text could inject the article
                val isUnknown = isInterpolationInjectionExclusion
                Some(new Exclusion(start, end, isUnknown))
              }
            }
            return ContainerUtil.createMaybeSingletonList(content.excludeRanges(excludedRanges.asJava))
          }
        case _ =>
      end match
    end if

    util.List.of()
  end buildTextContents

end ScalaTextExtractor
