package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion.{CompletionContributor, CompletionParameters, CompletionProvider, CompletionResultSet, CompletionType, PrioritizedLookupElement}
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder}
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.completion.ScalaLiteralTypeValuesCompletionContributor.ScalaLiteralTypeValuesCompletionProvider
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScOrType, ScType}

import scala.annotation.tailrec
import scala.jdk.CollectionConverters.IterableHasAsJava

final class ScalaLiteralTypeValuesCompletionContributor extends CompletionContributor {
  extend(
    CompletionType.BASIC,
    psiElement
      .isInScala3File
      .withParent(classOf[ScStringLiteral]),
    new ScalaLiteralTypeValuesCompletionProvider
  )

  extend(
    CompletionType.BASIC,
    identifierPattern
      .isInScala3File
      .withParent(classOf[ScReferenceExpression]),
    new ScalaLiteralTypeValuesCompletionProvider
  )
}

object ScalaLiteralTypeValuesCompletionContributor {
  private final class ScalaLiteralTypeValuesCompletionProvider extends CompletionProvider[CompletionParameters] {
    override def addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext,
                                result: CompletionResultSet): Unit = {
      val position = positionFromParameters(parameters)

      position.getContext match {
        case ref: ScReferenceExpression if !ref.isQualified =>
          val lookups = getLookupElements(ref)(_.value.presentation.toOption)
          result.addAllElements(lookups.asJava)
        case str: ScStringLiteral if str.isSimpleLiteral =>
          val stringTextOffset = str.getTextRange.getStartOffset
          // in case `str` is in a copy of the file, we need to modify offset to point to the right place in that file copy
          // SCL-17313
          val dummyOffset = parameters.getOffset - parameters.getPosition.getTextRange.getStartOffset + position.getTextRange.getStartOffset

          // use 0 as a string start offset
          val offsetInString = dummyOffset - stringTextOffset
          val contentRange = str.contentRange.shiftLeft(stringTextOffset)

          /**
           * `" foo<caret>"` by default uses `foo` as prefix inside Scala strings instead of ` foo`, the same applies to `"$foo<caret>"`
           *
           * @see [[com.intellij.codeInsight.completion.BaseCompletionService#suggestPrefix(com.intellij.codeInsight.completion.CompletionParameters)]]
           */
          val fixedPrefix =
            if (contentRange.contains(offsetInString)) {
              str.getText.substring(contentRange.getStartOffset, offsetInString)
            } else result.getPrefixMatcher.getPrefix

          val lookups = getLookupElements(str)(_.value.value.asOptionOfUnsafe[String])
          result
            .withPrefixMatcher(result.getPrefixMatcher.cloneWithPrefix(fixedPrefix))
            .addAllElements(lookups.asJava)

          if (lookups.nonEmpty) {
            result.stopHere()
          }
        case _ =>
      }
    }
  }

  private def getLookupElements(expr: ScExpression)(extractLookupString: ScLiteralType => Option[String]): Seq[LookupElement] = {
    val expectedType = expr.expectedType()
    expectedType.toList.flatMap { expectedType =>
      val literals = getLiteralTypes(expectedType)
      val lookupStrings = literals.flatMap(extractLookupString).toList

      lookupStrings.map { lookupString =>
        val lookup = LookupElementBuilder.create(lookupString)
        // prioritize items a bit to be higher
        // e.g.: higher than LegacyCompletionContributor items
        PrioritizedLookupElement.withExplicitProximity(lookup, 1)
      }
    }
  }

  private[this] def getLiteralTypes(raw: ScType): Set[ScLiteralType] = {
    @tailrec
    def recur(stack: List[ScType], types: Set[ScLiteralType]): Set[ScLiteralType] = stack match {
      case (literal: ScLiteralType) :: tail =>
        recur(tail, types + literal)
      case ScOrType(left, right) :: tail =>
        recur(left :: right :: tail, types)
      case _ :: tail =>
        recur(tail, types)
      case Nil => types
    }

    recur(List(raw.removeAliasDefinitions()), Set.empty)
  }
}
