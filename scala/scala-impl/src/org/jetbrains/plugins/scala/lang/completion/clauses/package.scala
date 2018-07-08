package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScalaType
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

import scala.collection.JavaConverters

package object clauses {

  private[clauses] implicit def tokenTypeString(tokenType: IElementType): String =
    tokenType.toString

  private[clauses] def findInheritors(clazz: PsiClass): Seq[ScTypeDefinition] = {
    import JavaConverters._
    ClassInheritorsSearch.search(clazz, clazz.resolveScope, false).asScala.collect {
      case definition: ScTypeDefinition => definition
    }.toSeq.sortBy(_.getNavigationElement.getTextRange.getStartOffset)
  }

  private[clauses] def patternTexts(definition: ScTypeDefinition)
                                   (implicit place: PsiElement): Seq[String] = {
    import NameSuggester._
    import ScalaTokenTypes.{tCOLON, tUNDER}

    val className = definition.name
    val defaultName: String = tUNDER

    val maybeText = definition match {
      case _: ScObject => Some(className)
      case scalaClass: ScClass =>
        val maybeNames = if (scalaClass.isCase) constructorParameters(scalaClass)
        else {
          val suggester = new UniqueNameSuggester(defaultName)
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
        .getOrElse(defaultName)
      s"$name$tCOLON $className"
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
