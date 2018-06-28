package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionType}
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScPattern, ScStableReferenceElementPattern}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScalaType
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

import scala.collection.JavaConverters

class CaseClauseCompletionContributor extends ScalaCompletionContributor {

  import CaseClauseCompletionContributor._

  extend(CompletionType.BASIC,
    PlatformPatterns.psiElement.inside(classOf[ScCaseClause]),
    new ScalaCompletionProvider {

      override protected def completionsFor(position: PsiElement)
                                           (implicit parameters: CompletionParameters, context: ProcessingContext): Iterable[ScalaLookupItem] = {
        val maybeClass = position.findContextOfType(classOf[ScStableReferenceElementPattern])
          .flatMap(_.expectedType)
          .flatMap(_.extractClass)

        val targetClasses = maybeClass match {
          case Some(scalaClass: ScTypeDefinition) if scalaClass.isSealed => findInheritors(scalaClass)
          case Some(scalaClass: ScTypeDefinition) => Seq(scalaClass)
          case _ => Iterable.empty
        }

        // TODO find conflicting CompletionContributor
        targetClasses.filterNot(_.isInstanceOf[ScObject]).map { clazz =>
          val result = new ScalaLookupItem(clazz, patternText(clazz, position))
          result.isLocalVariable = true
          result
        }
      }
    }
  )
}

object CaseClauseCompletionContributor {

  import ScalaTokenTypes.{tCOLON, tUNDER}

  def findInheritors(clazz: PsiClass): Seq[ScTypeDefinition] = {
    import JavaConverters._
    ClassInheritorsSearch.search(clazz, clazz.resolveScope, false).asScala.collect {
      case definition: ScTypeDefinition => definition
    }.toSeq.sortBy(_.getNavigationElement.getTextRange.getStartOffset)
  }

  def patternText(definition: ScTypeDefinition, place: PsiElement): String = {
    import NameSuggester._

    val className = definition.name
    val defaultName = tUNDER.toString

    val maybeText = definition match {
      case _: ScObject => Some(className)
      case scalaClass: ScClass =>
        val maybeNames = if (scalaClass.isCase) constructorParameters(scalaClass)
        else {
          val suggester = new UniqueNameSuggester(defaultName)
          extractorComponents(scalaClass, place).map(_.map(suggester))
        }

        maybeNames.map { names =>
          className + names.commaSeparated(parenthesize = true)
        }
      case _ => None
    }

    maybeText.getOrElse {
      val name = suggestNamesByType(ScalaType.designator(definition))
        .headOption
        .getOrElse(defaultName)
      s"$name$tCOLON $className"
    }
  }

  private[this] def constructorParameters(caseClass: ScClass): Option[Seq[String]] = for {
    constructor <- caseClass.constructor
    parametersList = constructor.effectiveFirstParameterSection
  } yield parametersList.map { parameter =>
    parameter.name + (if (parameter.isVarArgs) "@_*" else "")
  }

  private[this] def extractorComponents(scalaClass: ScClass, place: PsiElement) = {
    def findExtractor: ScTypeDefinition => Option[ScFunction] = {
      case scalaObject: ScObject => scalaObject.functions.find(_.isUnapplyMethod)
      case typeDefinition => typeDefinition.baseCompanionModule.flatMap(findExtractor)
    }

    for {
      extractor <- findExtractor(scalaClass)
      returnType <- extractor.returnType.toOption
    } yield ScPattern.extractorParameters(returnType, place, isOneArgCaseClass = false)
  }
}