package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion._
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.filters.position.{FilterPattern, LeftNeighbour}
import com.intellij.psi.filters.{AndFilter, ElementFilter, NotFilter, TextFilter}
import com.intellij.util.ProcessingContext

import scala.annotation.nowarn

final class ScalaKeywordCompletionContributor extends ScalaCompletionContributor {

  import ScalaKeyword._
  import ScalaKeywordCompletionContributor._
  import filters._

  private def registerFor(firstFilter: ElementFilter,
                          secondFilter: ElementFilter,
                          keywords: String*): Unit = extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement.and(new FilterPattern(new AndFilter(firstFilter, secondFilter))),
    new CompletionProvider[CompletionParameters] {

      override def addCompletions(parameters: CompletionParameters,
                                  context: ProcessingContext,
                                  resultSet: CompletionResultSet): Unit =
        lookups.ScalaKeywordLookupItem.addFor(resultSet, keywords: _*)
    }
  )

  private def registerStandardCompletion(filter: ElementFilter,
                                         keywords: String*): Unit = registerFor(
    new NotFilter(afterDotPattern),
    filter,
    keywords: _*
  )

  registerStandardCompletion(new toplevel.PackageFilter, PACKAGE)
  registerStandardCompletion(new expression.ExpressionFilter, TRUE, FALSE, NULL, NEW, SUPER, THIS)
  registerStandardCompletion(new modifiers.ModifiersFilter, PRIVATE, PROTECTED, OVERRIDE, ABSTRACT, FINAL, SEALED, IMPLICIT, LAZY)
  registerStandardCompletion(new modifiers.SoftModifiersFilter, INFIX, INLINE, OPAQUE, OPEN, TRANSPARENT)
  registerStandardCompletion(new modifiers.ImplicitFilter, IMPLICIT)
  registerStandardCompletion(new modifiers.InlineFilter, INLINE)
  registerStandardCompletion(new modifiers.UsingFilter, USING)
  registerStandardCompletion(new modifiers.GivenFilter, GIVEN)
  registerStandardCompletion(new modifiers.CaseFilter, CASE)
  registerStandardCompletion(new toplevel.EnumFilter, ENUM)
  registerStandardCompletion(new toplevel.ExtensionFilter, EXTENSION)
  registerStandardCompletion(new toplevel.ExportFilter, EXPORT)
  registerStandardCompletion(new toplevel.ImportFilter, IMPORT)
  registerStandardCompletion(new toplevel.TemplateFilter, CLASS, OBJECT)
  registerStandardCompletion(new toplevel.TraitFilter, TRAIT)
  registerStandardCompletion(new definitions.DefinitionsFilter, VAL, VAR)
  registerStandardCompletion(new definitions.ValueDefinitionFilter, VAL)
  registerStandardCompletion(new expression.StatementFilter, FOR, WHILE, DO, TRY, RETURN, THROW, IF)
  registerStandardCompletion(new expression.DoFilter, DO)
  registerStandardCompletion(new expression.DoYieldFilter, DO, YIELD)
  registerStandardCompletion(new expression.WhileFilter, WHILE)
  registerStandardCompletion(new expression.CatchFilter, CATCH)
  registerStandardCompletion(new expression.QuietCatchCaseFilter, CASE)
  registerStandardCompletion(new expression.FinallyFilter, FINALLY)
  registerStandardCompletion(new expression.ElseFilter, ELSE)
  registerStandardCompletion(new other.DerivesFilter, DERIVES)
  registerStandardCompletion(new other.ExtendsFilter, EXTENDS)
  registerStandardCompletion(new expression.YieldFilter, YIELD)
  registerStandardCompletion(new other.WithFilter, WITH)
  registerStandardCompletion(new definitions.DefTypeFilter, DEF, TYPE)
  registerStandardCompletion(new definitions.ExtensionDefFilter, DEF)
  registerStandardCompletion(new other.ForSomeFilter, FOR_SOME)
  registerStandardCompletion(new expression.MatchFilter, MATCH)
  registerStandardCompletion(new expression.IfFilter, IF)
  registerStandardCompletion(new expression.ThenFilter, THEN)
  registerFor(afterDotPattern, new other.TypeFilter, TYPE)

}

object ScalaKeywordCompletionContributor {

  private def afterDotPattern = new LeftNeighbour(new TextFilter(".")): @nowarn("cat=deprecation")
}
