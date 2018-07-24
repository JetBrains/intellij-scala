package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType, ScalaTypePresentation}
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

import scala.collection.JavaConverters

package object clauses {

  private[clauses] type NameAndElement = (String, PsiNamedElement)

  private[clauses] case class Inheritors(namedInheritors: Seq[ScTypeDefinition],
                                         anonymousInheritors: Seq[ScNewTemplateDefinition] = Seq.empty,
                                         javaInheritors: Seq[PsiClass] = Seq.empty) {

    import Inheritors._

    def patterns(exhaustive: Boolean = true)
                (implicit place: PsiElement): Seq[NameAndElement] = {
      val anonymousInheritorsPatterns = anonymousInheritors.map(patternTexts)
      val maybeWildcard = if (exhaustive && anonymousInheritorsPatterns.exists(_.isEmpty)) Some(DefaultName -> null)
      else None

      anonymousInheritorsPatterns.flatten ++
        namedInheritors.flatMap(patternTexts(_, exhaustive)) ++
        javaInheritors.map(defaultPatternText) ++
        maybeWildcard
    }
  }

  private[clauses] object Inheritors {

    private val DefaultName = "_"

    def apply(classes: Seq[PsiClass]): Inheritors = {
      val (scalaInheritors, javaInheritors) = classes.partition(_.isInstanceOf[ScTemplateDefinition])
      val (namedInheritors, anonymousInheritors) = scalaInheritors.partition(_.isInstanceOf[ScTypeDefinition])

      Inheritors(
        namedInheritors.map(_.asInstanceOf[ScTypeDefinition]),
        anonymousInheritors.map(_.asInstanceOf[ScNewTemplateDefinition]),
        javaInheritors
      )
    }

    private def patternTexts(definition: ScNewTemplateDefinition)
                            (implicit place: PsiElement) =
      PsiTreeUtil.getContextOfType(definition, classOf[ScPatternDefinition]) match {
        case value@ScPatternDefinition.expr(`definition`) => stableNames(value)
        case _ => Seq.empty
      }

    private def patternTexts(definition: ScTypeDefinition, exhaustive: Boolean)
                            (implicit place: PsiElement): Traversable[NameAndElement] = {
      def extractorPatternText(pair: (ScObject, Seq[String])) = {
        val (scalaObject, names) = pair
        adjustedTypeText(scalaObject) + names.commaSeparated(model = Model.Parentheses) -> scalaObject
      }

      definition match {
        case scalaObject: ScObject if exhaustive =>
          val pattern = adjustedTypeText(scalaObject)
          Some(pattern -> scalaObject)
        case _: ScObject => None
        case scalaClass: ScClass if scalaClass.isCase =>
          scalaClass.fakeCompanionModule
            .zip(constructorParameters(scalaClass))
            .map(extractorPatternText)
        case _ =>
          val maybeInexhaustivePatternText = extractorComponents(definition).map(extractorPatternText)

          val maybeDefaultPatternText = if (exhaustive) Some(defaultPatternText(definition))
          else None

          maybeInexhaustivePatternText ++ maybeDefaultPatternText
      }
    }

    private def defaultPatternText(clazz: PsiClass)
                                  (implicit place: PsiElement) = {
      val scType = clazz match {
        case definition: ScTemplateDefinition => clauses.adjustedType(definition)
        case _ =>
          val typeElement = ScalaPsiElementFactory.createTypeElementFromText(clazz.qualifiedName, place.getContext, place)
          ScalaPsiUtil.adjustTypes(typeElement, addImports = false)
          typeElement.`type`().getOrAny
      }

      val (adjustedType, typeArguments) = scType match {
        case ScParameterizedType(designator, arguments) =>
          (designator, Seq.fill(arguments.length)(DefaultName).commaSeparated(model = Model.SquareBrackets))
        case _ => (scType, "")
      }

      val name = NameSuggester.suggestNamesByType(adjustedType)
        .headOption.getOrElse(DefaultName)

      s"$name: ${adjustedType.presentableText}$typeArguments" -> clazz
    }

    private[this] def stableNames(value: ScPatternDefinition)
                                 (implicit place: PsiElement) = value.containingClass match {
      case scalaObject: ScObject => declaredNamesPatterns(value, adjustedType(scalaObject))
      case _ => Seq.empty
    }

    private[this] def constructorParameters(caseClass: ScClass): Option[Seq[String]] = for {
      constructor <- caseClass.constructor
      parametersList = constructor.effectiveFirstParameterSection
    } yield parametersList.map { parameter =>
      parameter.name + (if (parameter.isVarArgs) "@_*" else "")
    }

    private[this] def extractorComponents(definition: ScTypeDefinition)
                                         (implicit place: PsiElement) = for {
      companion <- definition.baseCompanionModule
      if companion.isInstanceOf[ScObject]

      extractor <- companion.functions.find(_.isUnapplyMethod)
      returnType <- extractor.returnType.toOption

      suggester = new NameSuggester.UniqueNameSuggester(DefaultName)
      types = ScPattern.extractorParameters(returnType, place, isOneArgCaseClass = false)
    } yield (companion.asInstanceOf[ScObject], types.map(suggester))
  }

  private[clauses] object SealedDefinition {

    def unapply(definition: ScTypeDefinition): Option[Seq[PsiClass]] = if (definition.isSealed) {
      import JavaConverters._
      val inheritors = DirectClassInheritorsSearch
        .search(definition, definition.resolveScope)
        .findAll()
        .asScala
        .toSeq
        .sortBy(_.getNavigationElement.getTextRange.getStartOffset)
      Some(inheritors)
    } else None
  }

  private[clauses] def declaredNamesPatterns(value: ScValue, designator: ScType)
                                            (implicit place: PsiElement): Seq[NameAndElement] = {
    val typeText = objectText(designator)
    value.declaredElements.map { element =>
      s"$typeText.${element.name}" -> element
    }
  }

  private[clauses] def adjustedType(definition: ScTemplateDefinition): ScType =
    definition.getTypeWithProjections(true).getOrAny

  private[clauses] def objectText(designator: ScType)
                                 (implicit place: PsiElement): String =
    designator.presentableText
      .stripSuffix(ScalaTypePresentation.ObjectTypeSuffix)

  private[this] def adjustedTypeText(scalaObject: ScObject)
                                    (implicit place: PsiElement): String =
    objectText(adjustedType(scalaObject))
}
