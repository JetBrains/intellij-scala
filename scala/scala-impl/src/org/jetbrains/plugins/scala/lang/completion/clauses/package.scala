package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ClassInheritorsSearch
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScPatternDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScalaType
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

import scala.collection.JavaConverters

package object clauses {

  private[clauses] val DefaultName = "_"

  private[clauses] case class Inheritors(namedInheritors: Seq[ScTypeDefinition],
                                         anonymousInheritors: Seq[ScNewTemplateDefinition])

  private[clauses] object SealedDefinition {

    def unapply(sealedDefinition: ScTypeDefinition): Option[Inheritors] = if (sealedDefinition.isSealed) {
      import JavaConverters._
      val inheritors = ClassInheritorsSearch.search(sealedDefinition, sealedDefinition.resolveScope, false).asScala.toSeq
        .sortBy(_.getNavigationElement.getTextRange.getStartOffset)

      val (namedInheritors, anonymousInheritors) = inheritors.partition(_.isInstanceOf[ScTypeDefinition])
      val namedDefinitions = namedInheritors.map(_.asInstanceOf[ScTypeDefinition])
      val anonymousDefinitions = anonymousInheritors.map(_.asInstanceOf[ScNewTemplateDefinition])

      Some(Inheritors(namedDefinitions, anonymousDefinitions))
    } else None
  }

  private[clauses] def patternTexts(definition: ScNewTemplateDefinition): Seq[String] = {
    def stableNames(value: ScPatternDefinition) = value.containingClass match {
      case scalaObject: ScObject if scalaObject.isStatic =>
        val objectName = scalaObject.name
        value.declaredNames.map(name => s"$objectName.$name")
      case _ => Seq.empty
    }

    definition.findContextOfType(classOf[ScPatternDefinition]).collect {
      case value@ScPatternDefinition.expr(`definition`) => value
    }.toSeq.flatMap(stableNames)
  }

  private[clauses] def patternTexts(definition: ScTypeDefinition)
                                   (implicit place: PsiElement): Seq[String] = {
    import NameSuggester._
    val className = definition.name

    val maybeText = definition match {
      case _: ScObject => Some(className)
      case scalaClass: ScClass =>
        val maybeNames = if (scalaClass.isCase) constructorParameters(scalaClass)
        else {
          val suggester = new UniqueNameSuggester(DefaultName)
          extractorComponents(scalaClass).map(_.map(suggester))
        }

        maybeNames.map { names =>
          className + names.commaSeparated(parenthesize = true)
        }
      case _ => None
    }

    def defaultText = {
      val name = suggestNamesByType(ScalaType.designator(definition))
        .headOption
        .getOrElse(DefaultName)
      s"$name: $className"
    }

    (definition, maybeText) match {
      case (scalaClass: ScClass, Some(text)) if !scalaClass.isCase => Seq(text, defaultText)
      case (_, Some(text)) => Seq(text)
      case _ => Seq(defaultText)
    }
  }

  private[clauses] def findExtractor: ScTypeDefinition => Option[ScFunction] = {
    case scalaObject: ScObject => scalaObject.functions.find(_.isUnapplyMethod)
    case typeDefinition => typeDefinition.baseCompanionModule.flatMap(findExtractor)
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
