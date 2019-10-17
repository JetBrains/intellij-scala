package org.jetbrains.plugins.scala.lang.completion
package ml

import java.util

import com.intellij.codeInsight.completion.CompletionLocation
import com.intellij.codeInsight.completion.ml.{ContextFeatures, ElementFeatureProvider, MLFeatureValue}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyKey
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.completion.ml.ScalaElementFeatureProvider._
import org.jetbrains.plugins.scala.lang.completion.weighter.ScalaByExpectedTypeWeigher
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

final class ScalaElementFeatureProvider extends ElementFeatureProvider {

  override def getName: String = "scala"

  override def calculateFeatures(element: LookupElement, location: CompletionLocation, contextFeatures: ContextFeatures): util.Map[String, MLFeatureValue] = {

    implicit val position: PsiElement = Position.getValue(location)
    implicit val project: Project = location.getProject

    val scalaLookupItem = element match {
      case ScalaLookupItem(item, _) => Some(item)
      case _ => None
    }

    val maybeElement = scalaLookupItem.map(_.element)
    val maybeName = scalaLookupItem.map(_.name)

    val (expectedTypeWords, expectedNameWords) = ExpectedTypeAndNameWords.getValue(location)

    val calculateWords = expectedTypeWords.nonEmpty || expectedNameWords.nonEmpty

    val nameWords = if (calculateWords) extractWords(maybeName) else Array.empty[String]

    val maybeType = scalaLookupItem
      .filter(_ => calculateWords)
      .flatMap(item => ScalaByExpectedTypeWeigher.computeType(item.element, item.substitutor))

    val typeWords = extractWords(maybeType)

    val kind = elementKind(maybeElement).getOrElse {
      element.getObject match {
        case string: String if ScalaTokenTypes.KEYWORDS.getTypes.exists(_.toString == string) => CompletionItem.KEYWORD
        case _ => CompletionItem.UNKNOWN
      }
    }

    val features = new util.HashMap[String, MLFeatureValue](Context.getValue(location))

    features.put("kind", MLFeatureValue.categorical(kind))
    features.put("symbolic", MLFeatureValue.binary(maybeName.exists(isSymbolic)))
    features.put("unary", MLFeatureValue.binary(maybeName.exists(_.startsWith("unary_"))))
    features.put("scala", MLFeatureValue.binary(scalaLookupItem.exists(_.element.isInstanceOf[ScalaPsiElement])))
    features.put("java_object_method", MLFeatureValue.binary(isJavaObjectMethod(maybeElement)))
    features.put("argument_count", MLFeatureValue.float(argumentCount(maybeElement).getOrElse(-1)))
    features.put("name_name_sim", MLFeatureValue.float(wordsSimilarity(expectedNameWords, nameWords).getOrElse(-1.0)))
    features.put("name_type_sim", MLFeatureValue.float(wordsSimilarity(expectedNameWords, typeWords).getOrElse(-1.0)))
    features.put("type_name_sim", MLFeatureValue.float(wordsSimilarity(expectedTypeWords, nameWords).getOrElse(-1.0)))
    features.put("type_type_sim", MLFeatureValue.float(wordsSimilarity(expectedTypeWords, typeWords).getOrElse(-1.0)))

    features
  }
}

object ScalaElementFeatureProvider {

  private val Position = NotNullLazyKey.create[PsiElement, CompletionLocation]("scala.feature.element.position", location => {
    positionFromParameters(location.getCompletionParameters)
  })

  private val ExpectedTypeAndNameWords = NotNullLazyKey.create[(Array[String],  Array[String]), CompletionLocation]("scala.feature.element.expected.type.and.name.words", location => {
    val position = Position.getValue(location)
    val expressionOption = definitionByPosition(position, ScalaAfterNewCompletionContributor.isAfterNew(position))

    val expectedTypeAndName = expressionOption
      .flatMap(_.expectedTypeEx())
      .map { case (expectedType, typeElement) => expectedType -> expectedName(typeElement) }

    val expectedType = expectedTypeAndName.map(_._1)
    val expetedName = expectedTypeAndName.flatMap(_._2).orElse(expectedName(Option(position)))

    extractWords(expectedType) -> extractWords(expetedName)
  })

  private val Context = NotNullLazyKey.create[util.HashMap[String, MLFeatureValue], CompletionLocation]("scala.feature.element.context", location => {
    val position = Position.getValue(location)

    val contextFeatures = new util.HashMap[String, MLFeatureValue]

    // theses features should be moved to context after IntellijIdeaRulezzz fix

    contextFeatures.put("postfix", MLFeatureValue.binary(isPostfix(Option(position))))
    contextFeatures.put("type_expected", MLFeatureValue.binary(ScalaAfterNewCompletionContributor.isInTypeElement(position, Some(location))))
    contextFeatures.put("after_new", MLFeatureValue.binary(ScalaAfterNewCompletionContributor.isAfterNew(position)))
    contextFeatures.put("inside_catch", MLFeatureValue.binary(isInsideCatch(Option(position))))

    contextFeatures
  })
}
