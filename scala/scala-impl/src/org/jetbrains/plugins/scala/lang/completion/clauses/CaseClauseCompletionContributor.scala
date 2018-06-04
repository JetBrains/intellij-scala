package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion.{CompletionParameters, CompletionType}
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScCaseClauses, ScPattern, ScStableReferenceElementPattern}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScMatchStmt
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.NameSuggester

import scala.collection.mutable

class CaseClauseCompletionContributor extends ScalaCompletionContributor {

  import CaseClauseCompletionContributor._

  extend(CompletionType.BASIC,
    PlatformPatterns.psiElement.inside(classOf[ScCaseClause]),
    new ScalaCompletionProvider {

      override protected def completionsFor(position: PsiElement)
                                           (implicit parameters: CompletionParameters, context: ProcessingContext): Iterable[ScalaLookupItem] =
        for {
          caseClause <- position.findContextOfType(classOf[ScCaseClause])
          if caseClause.pattern.exists(isValidPattern(_, position))
          (scalaClass, statement) <- targetExpressionClass(caseClause)
        } yield {
          val result = new ScalaLookupItem(scalaClass, patternText(scalaClass, statement))
          result.isLocalVariable = true
          result
        }
    }
  )
}

object CaseClauseCompletionContributor {

  def patternText(definition: ScTypeDefinition, statement: ScMatchStmt): String = {
    val className = definition.name

    val maybeText = definition match {
      case _: ScObject => Some(className)
      case scalaClass: ScClass =>
        val maybeNames = if (scalaClass.isCase) constructorParameters(scalaClass)
        else extractorComponents(scalaClass, statement)

        maybeNames.map { names =>
          className + names.commaSeparated(parenthesize = true)
        }
      case _ => None
    }

    maybeText.getOrElse("_: " + className)
  }


  private def isValidPattern(pattern: ScPattern, position: PsiElement) = pattern match {
    case _: ScStableReferenceElementPattern => pattern.isAncestorOf(position)
    case _ => false
  }

  private def targetExpressionClass(caseClause: ScCaseClause) = caseClause.getContext match {
    case caseClauses: ScCaseClauses => caseClauses.getContext match {
      case statement@ScMatchStmt(Typeable(ExtractClass(scalaClass: ScClass)), _) => Some(scalaClass, statement)
      case _ => None
    }
    case _ => None
  }

  private def constructorParameters(caseClass: ScClass): Option[Seq[String]] = for {
    constructor <- caseClass.constructor
    parametersList = constructor.effectiveFirstParameterSection
  } yield parametersList.map { parameter =>
    parameter.name + (if (parameter.isVarArgs) "@_*" else "")
  }

  private def extractorComponents(scalaClass: ScClass, statement: ScMatchStmt) = {
    def findExtractor: ScTypeDefinition => Option[ScFunction] = {
      case scalaObject: ScObject => scalaObject.functions.find(_.isUnapplyMethod)
      case typeDefinition => typeDefinition.baseCompanionModule.flatMap(findExtractor)
    }

    def validNames(types: Seq[ScType]) = {
      val nameValidator = mutable.Map.empty[String, Int].withDefaultValue(-1)

      types.map { `type` =>
        NameSuggester.suggestNamesByType(`type`).headOption.map { name =>
          nameValidator(name) += 1

          name + (nameValidator(name) match {
            case 0 => ""
            case i => i
          })
        }.getOrElse("_")
      }
    }

    for {
      extractor <- findExtractor(scalaClass)
      returnType <- extractor.returnType.toOption
      types = ScPattern.extractorParameters(returnType, statement, isOneArgCaseClass = false)
    } yield validNames(types)
  }
}