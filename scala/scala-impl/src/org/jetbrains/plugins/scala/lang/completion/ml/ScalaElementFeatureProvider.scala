package org.jetbrains.plugins.scala.lang.completion.ml

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
import org.jetbrains.plugins.scala.lang.completion.{ScalaAfterNewCompletionContributor, ScalaSmartCompletionContributor, positionFromParameters}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

class ScalaElementFeatureProvider extends ElementFeatureProvider {

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

    val nameWords = if (calculateWords) NameFeaturesUtil.extractWords(maybeName) else Array.empty[String]

    val maybeType = scalaLookupItem
      .filter(_ => calculateWords)
      .flatMap(item => ScalaByExpectedTypeWeigher.computeType(item.element, item.substitutor))

    val typeWords = NameFeaturesUtil.extractWords(maybeType)

    val kind = PsiFeaturesUtil.kind(maybeElement).getOrElse {
      element.getObject match {
        case string: String if ScalaTokenTypes.KEYWORDS.getTypes.exists(_.toString == string) => ItemKind.KEYWORD
        case _ => ItemKind.UNKNOWN
      }
    }

    val features = new util.HashMap[String, MLFeatureValue](Context.getValue(location))

    features.put("kind", MLFeatureValue.categorical(kind))
    features.put("symbolic", MLFeatureValue.binary(maybeName.exists(NameFeaturesUtil.isSymbolic)))
    features.put("unary", MLFeatureValue.binary(maybeName.exists(_.startsWith("unary_"))))
    features.put("scala", MLFeatureValue.binary(scalaLookupItem.exists(_.element.isInstanceOf[ScalaPsiElement])))
    features.put("java_object_method", MLFeatureValue.binary(PsiFeaturesUtil.isJavaObjectMethod(maybeElement)))
    features.put("argument_count", MLFeatureValue.float(PsiFeaturesUtil.argumentCount(maybeElement)))
    features.put("name_name_sim", MLFeatureValue.float(NameFeaturesUtil.wordsSimilarity(expectedNameWords, nameWords)))
    features.put("name_type_sim", MLFeatureValue.float(NameFeaturesUtil.wordsSimilarity(expectedNameWords, typeWords)))
    features.put("type_name_sim", MLFeatureValue.float(NameFeaturesUtil.wordsSimilarity(expectedTypeWords, nameWords)))
    features.put("type_type_sim", MLFeatureValue.float(NameFeaturesUtil.wordsSimilarity(expectedTypeWords, typeWords)))

    features
  }
}

object ScalaElementFeatureProvider {

  private val Position = NotNullLazyKey.create[PsiElement, CompletionLocation]("scala.feature.element.position", location => {
    positionFromParameters(location.getCompletionParameters)
  })

  private val ExpectedTypeAndNameWords = NotNullLazyKey.create[(Array[String],  Array[String]), CompletionLocation]("scala.feature.element.expected.type.and.name.words", location => {
    val position = Position.getValue(location)

    val expressionOption = position match {
      case ScalaSmartCompletionContributor.Reference(reference) => Some(reference)
      case _ if ScalaAfterNewCompletionContributor.isAfterNew(position) => ScalaAfterNewCompletionContributor.findNewTemplate(position)
      case _ => None
    }

    val expectedTypeAndName = expressionOption
      .flatMap(_.expectedTypeEx())
      .map { case (expectedType, typeElement) => expectedType -> PsiFeaturesUtil.expectedName(typeElement) }

    val expectedType = expectedTypeAndName.map(_._1)
    val expetedName = expectedTypeAndName.flatMap(_._2).orElse(PsiFeaturesUtil.expectedName(Option(position)))

    NameFeaturesUtil.extractWords(expectedType) -> NameFeaturesUtil.extractWords(expetedName)
  })

  private val Context = NotNullLazyKey.create[util.HashMap[String, MLFeatureValue], CompletionLocation]("scala.feature.element.context", location => {
    val posision = Position.getValue(location)

    val contextFeatures = new util.HashMap[String, MLFeatureValue]

    // theses features should be moved to context after IntellijIdeaRulezzz fix

    contextFeatures.put("postfix", MLFeatureValue.binary(PsiFeaturesUtil.postfix(Option(posision))))
    contextFeatures.put("type_expected", MLFeatureValue.binary(ScalaAfterNewCompletionContributor.isInTypeElement(posision, Some(location))))
    contextFeatures.put("after_new", MLFeatureValue.binary(ScalaAfterNewCompletionContributor.isAfterNew(posision)))
    contextFeatures.put("inside_catch", MLFeatureValue.binary(PsiFeaturesUtil.insideCatch(Option(posision))))

    contextFeatures
  })
}
