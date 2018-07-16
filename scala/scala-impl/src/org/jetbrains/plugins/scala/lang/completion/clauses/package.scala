package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScPatternDefinition, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScalaType
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

import scala.collection.JavaConverters

package object clauses {

  private[clauses] type NameAndElement = (String, PsiNamedElement)

  private[clauses] case class Inheritors(namedInheritors: Seq[ScTypeDefinition],
                                         anonymousInheritors: Seq[ScNewTemplateDefinition] = Seq.empty,
                                         javaInheritors: Seq[PsiClass] = Seq.empty) {

    import Inheritors._

    def patterns(implicit place: PsiElement): Seq[NameAndElement] = {
      val anonymousInheritorsPatterns = anonymousInheritors.map(patternTexts)
      val maybeWildcard = if (anonymousInheritorsPatterns.exists(_.isEmpty)) Some(DefaultName -> null)
      else None

      anonymousInheritorsPatterns.flatten ++
        namedInheritors.flatMap(patternTexts) ++
        javaInheritors.map(patternText) ++
        maybeWildcard
    }
  }

  private[clauses] object Inheritors {

    private val DefaultName = "_"

    def apply(inheritors: Seq[PsiClass]): Inheritors = {
      val (scalaInheritors, javaInheritors) = inheritors.partition(_.isInstanceOf[ScTemplateDefinition])
      val (namedInheritors, anonymousInheritors) = scalaInheritors.partition(_.isInstanceOf[ScTypeDefinition])

      Inheritors(
        namedInheritors.map(_.asInstanceOf[ScTypeDefinition]),
        anonymousInheritors.map(_.asInstanceOf[ScNewTemplateDefinition]),
        javaInheritors
      )
    }

    private def patternTexts(definition: ScNewTemplateDefinition) =
      PsiTreeUtil.getContextOfType(definition, classOf[ScPatternDefinition]) match {
        case value@ScPatternDefinition.expr(`definition`) => stableNames(value)
        case _ => Seq.empty
      }

    private def patternTexts(definition: ScTypeDefinition)
                            (implicit place: PsiElement): Seq[NameAndElement] = {
      val maybeText = definition match {
        case scalaObject: ScObject => Some(scalaObject.name -> scalaObject)
        case scalaClass: ScClass =>
          val maybeNames = if (scalaClass.isCase) constructorParameters(scalaClass)
          else {
            val suggester = new NameSuggester.UniqueNameSuggester(DefaultName)
            extractorComponents(scalaClass).map(_.map(suggester))
          }

          maybeNames.map { names =>
            (scalaClass.name + names.commaSeparated(parenthesize = true)) -> scalaClass
          }
        case _ => None
      }

      (definition, maybeText) match {
        case (scalaClass: ScClass, Some(pair)) if !scalaClass.isCase => Seq(pair, patternText(scalaClass))
        case (_, Some(pair)) => Seq(pair)
        case _ => Seq(patternText(definition))
      }
    }

    private def patternText(clazz: PsiClass) = {
      val designatorType = ScalaType.designator(clazz)
      val name = NameSuggester.suggestNamesByType(designatorType)
        .headOption
        .getOrElse(DefaultName)
      s"$name: ${clazz.name}" -> clazz
    }

    private[this] def stableNames(value: ScPatternDefinition) = value.containingClass match {
      case scalaObject: ScObject if scalaObject.isStatic =>
        declaredNamesPatterns(value, scalaObject)
      case _ => Seq.empty
    }

    private[this] def constructorParameters(caseClass: ScClass): Option[Seq[String]] = for {
      constructor <- caseClass.constructor
      parametersList = constructor.effectiveFirstParameterSection
    } yield parametersList.map { parameter =>
      parameter.name + (if (parameter.isVarArgs) "@_*" else "")
    }

    private[this] def extractorComponents(scalaClass: ScClass)
                                         (implicit place: PsiElement) = for {
      extractor <- findExtractor(scalaClass)
      returnType <- extractor.returnType.toOption
    } yield ScPattern.extractorParameters(returnType, place, isOneArgCaseClass = false)
  }

  private[clauses] object SealedDefinition {

    def unapply(definition: ScTypeDefinition): Option[Inheritors] = if (definition.isSealed) {
      import JavaConverters._
      val inheritors = ClassInheritorsSearch.search(definition, definition.resolveScope, false)
        .findAll()
        .asScala
        .toSeq
        .sortBy(_.getNavigationElement.getTextRange.getStartOffset)

      Some(Inheritors(inheritors))
    } else None
  }

  private[clauses] def declaredNamesPatterns(value: ScValue,
                                             scalaObject: ScObject): Seq[NameAndElement] =
    declaredNamesPatterns(value.declaredElements, scalaObject)

  private[clauses] def declaredNamesPatterns(declaredElements: Seq[PsiNamedElement], clazz: PsiClass): Seq[NameAndElement] = {
    val className = clazz.name
    declaredElements.map(element => s"$className.${element.name}" -> element)
  }

  private[clauses] def findExtractor: ScTypeDefinition => Option[ScFunction] = {
    case scalaObject: ScObject => scalaObject.functions.find(_.isUnapplyMethod)
    case typeDefinition => typeDefinition.baseCompanionModule.flatMap(findExtractor)
  }
}
