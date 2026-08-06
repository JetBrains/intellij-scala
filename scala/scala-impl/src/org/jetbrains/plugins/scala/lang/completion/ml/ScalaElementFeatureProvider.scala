package org.jetbrains.plugins.scala.lang.completion.ml

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.{ContextFeatures, ElementFeatureProvider, MLFeatureValue}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyKey
import com.intellij.patterns.{ElementPattern, PlatformPatterns}
import com.intellij.psi.PsiElement
import com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY
import org.jetbrains.plugins.scala.ScalaLowerCase
import org.jetbrains.plugins.scala.lang.completion.{ScalaKeyword, afterNewKeywordPattern, definitionByPosition, identifierWithParentsPattern, insideTypePattern}
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.completion.ml.ScalaElementFeatureProvider._
import org.jetbrains.plugins.scala.lang.completion.weighter.ScalaByExpectedTypeWeigher.computeType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScCatchBlock, ScPostfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.refactoring.ScalaNamesValidator.isKeyword

import java.util

final class ScalaElementFeatureProvider extends ElementFeatureProvider {

  override def getName: String = ScalaLowerCase

  override def calculateFeatures(element: LookupElement, location: CompletionLocation, contextFeatures: ContextFeatures): util.Map[String, MLFeatureValue] = {
    implicit val position: PsiElement = location.getCompletionParameters.getPosition
    implicit val project: Project = location.getProject

    val keyword = element.getObject match {
      case string: String if isKeyword(string) => string
      case _ => null
    }

    val psiElement = element.getPsiElement
    val lookupString = element.getLookupString

    val kind = if (keyword == null)
      if (psiElement == null)
        CompletionItem.UNKNOWN
      else
        elementKind(psiElement)
    else
      CompletionItem.KEYWORD

    val features = new util.HashMap[String, MLFeatureValue](Context.getValue(location))
    features.put("kind", MLFeatureValue.categorical(kind))
    features.put("keyword", MLFeatureValue.categorical(KeywordsByName(keyword)))

    def putBinary(kind: String, value: Boolean): Unit =
      features.put(kind, MLFeatureValue.binary(value))

    putBinary("symbolic", isSymbolic(lookupString))
    putBinary("unary", lookupString.startsWith("unary_"))
    putBinary("scala", psiElement.isInstanceOf[ScalaPsiElement])
    putBinary("java_object_method", isJavaObjectMethod(psiElement))

    val (expectedTypeWords, expectedNameWords) = ExpectedTypeAndNameWords.getValue(location)

    val (actualTypeWords, actualNameWords) =
      if (expectedTypeWords.isEmpty && expectedNameWords.isEmpty) {
        (EMPTY_STRING_ARRAY, EMPTY_STRING_ARRAY)
      } else {
        val typeWords = element match {
          case ScalaLookupItem(item, psiElement) =>
            computeType(psiElement, item.substitutor).fold(EMPTY_STRING_ARRAY)(extractWords)
          case _ =>
            keyword match {
              case ScalaKeyword.TRUE |
                   ScalaKeyword.FALSE => Array("boolean")
              case _ => EMPTY_STRING_ARRAY
            }
        }

        (typeWords, extractWords(lookupString))
      }

    def putFloat(kind: String, value: Double): Unit =
      features.put(kind, MLFeatureValue.float(value))

    putFloat("argument_count", argumentsCount(psiElement))
    putFloat("name_name_sim", wordsSimilarity(expectedNameWords, actualNameWords))
    putFloat("name_type_sim", wordsSimilarity(expectedNameWords, actualTypeWords))
    putFloat("type_name_sim", wordsSimilarity(expectedTypeWords, actualNameWords))
    putFloat("type_type_sim", wordsSimilarity(expectedTypeWords, actualTypeWords))

    features
  }
}

object ScalaElementFeatureProvider {

  private val ExpectedTypeAndNameWords = NotNullLazyKey.createLazyKey[(Array[String], Array[String]), CompletionLocation]("scala.feature.element.expected.type.and.name.words", location => {
    val position = location.getCompletionParameters.getPosition

    val expectedTypeAndName = definitionByPosition(position).flatMap {
      _.expectedTypeEx()
    }.map {
      case (expectedType, maybeTypeElement) => expectedType -> maybeTypeElement
    }

    val expectedTypeWords = expectedTypeAndName.fold(EMPTY_STRING_ARRAY) {
      case (expectedType, _) => extractWords(expectedType)
    }

    val expectedNameWords = expectedTypeAndName.flatMap {
      case (_, Some(typeElement)) => expectedName(typeElement)
      case _ => None
    }.orElse {
      expectedName(position)
    }.fold(EMPTY_STRING_ARRAY) {
      extractWords(_)
    }

    expectedTypeWords -> expectedNameWords
  })

  private val Context = NotNullLazyKey.createLazyKey[util.HashMap[String, MLFeatureValue], CompletionLocation]("scala.feature.element.context", location => {
    val position = location.getCompletionParameters.getPosition
    val processingContext = location.getProcessingContext

    // theses features should be moved to context after IntellijIdeaRulezzz fix

    val contextFeatures = new util.HashMap[String, MLFeatureValue]

    def put(kind: String, pattern: ElementPattern[_ <: PsiElement]): Unit =
      contextFeatures.put(
        kind,
        MLFeatureValue.binary(pattern.accepts(position, processingContext))
      )

    put("postfix", identifierWithParentsPattern(classOf[ScReferenceExpression], classOf[ScPostfixExpr]))
    put("type_expected", insideTypePattern)
    put("after_new", afterNewKeywordPattern)
    put("inside_catch", PlatformPatterns.psiElement.inside(classOf[ScCatchBlock]))

    contextFeatures
  })
}
