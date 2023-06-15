package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.filters.position.FilterPattern
import com.intellij.psi.filters.{AndFilter, ElementFilter, NotFilter, OrFilter}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiErrorElement, PsiWhiteSpace}
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.completion.filters.toplevel.IsTopLevelElementInProductionScalaFileFilter
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

final class ScalaKeywordCompletionContributor extends ScalaCompletionContributor {

  import ScalaKeyword._
  import ScalaKeywordCompletionContributor._
  import filters._

  private def registerFor(
    filters: Seq[ElementFilter],
    keywords: String*
  ): Unit = extend(
    CompletionType.BASIC,
    PlatformPatterns.psiElement.and(new FilterPattern(new AndFilter(filters: _*))),
    new CompletionProvider[CompletionParameters] {
      override def addCompletions(parameters: CompletionParameters,
                                  context: ProcessingContext,
                                  resultSet: CompletionResultSet): Unit =
        lookups.ScalaKeywordLookupItem.addFor(resultSet, keywords: _*)
    }
  )

  private def registerStandardCompletion(
    filter: ElementFilter,
    keywords: String*
  ): Unit = registerFor(
    Seq(not(AfterDotFilter), filter),
    keywords: _*
  )

  private def registerStandardCompletionForModifier(
    filter: ElementFilter,
    keywords: String*
  ): Unit = registerFor(
    Seq(
      not(AfterDotFilter),
      not(AfterSemicolonOrNewLineExpectedErrorFilter),
      filter
    ),
    keywords: _*
  )

  private def registerStandardCompletionForDefinitionKeyword(
    filter: ElementFilter,
    keywords: String*
  ): Unit = registerFor(
    Seq(
      not(AfterDotFilter),
      not(AfterSemicolonOrNewLineExpectedErrorFilter),
      filter
    ),
    keywords: _*
  )

  private def registerStandardCompletionForDefinitionKeywordNotTopLevelInScala3(
    filter: ElementFilter,
    keywords: String*
  ): Unit = registerFor(
    Seq(
      not(AfterDotFilter),
      not(AfterSemicolonOrNewLineExpectedErrorFilter),
      or(not(IsTopLevelElementInProductionScalaFileFilter), IsInScala3ModuleFilter),
      filter
    ),
    keywords: _*
  )

  private def registerStandardCompletionForExpression(
    filter: ElementFilter,
    keywords: String*
  ): Unit = registerFor(
    Seq(
      not(AfterDotFilter),
      not(AfterSemicolonOrNewLineExpectedErrorFilter),
      not(IsTopLevelElementInProductionScalaFileFilter),
      filter
    ),
    keywords: _*
  )

  registerStandardCompletionForModifier(new modifiers.ModifiersFilter, PRIVATE, PROTECTED, OVERRIDE, ABSTRACT, FINAL, SEALED, IMPLICIT, LAZY)
  registerStandardCompletionForModifier(new modifiers.SoftModifiersFilter, INFIX, INLINE, OPAQUE, OPEN, TRANSPARENT)
  registerStandardCompletionForModifier(new modifiers.ImplicitFilter, IMPLICIT)
  registerStandardCompletionForModifier(new modifiers.InlineFilter, INLINE)
  registerStandardCompletionForModifier(new modifiers.UsingFilter, USING)
  registerStandardCompletionForModifier(new modifiers.GivenFilter, GIVEN)
  registerStandardCompletionForModifier(new modifiers.CaseFilter, CASE)

  registerStandardCompletionForDefinitionKeyword(new toplevel.PackageFilter, PACKAGE)
  registerStandardCompletionForDefinitionKeyword(new toplevel.ExportFilter, EXPORT)
  registerStandardCompletionForDefinitionKeyword(new toplevel.ImportFilter, IMPORT)
  registerStandardCompletionForDefinitionKeyword(new toplevel.TemplateFilter, CLASS, OBJECT)
  registerStandardCompletionForDefinitionKeyword(new toplevel.TraitFilter, TRAIT)
  registerStandardCompletionForDefinitionKeyword(new toplevel.EnumFilter, ENUM)
  registerStandardCompletionForDefinitionKeyword(new toplevel.ExtensionFilter, EXTENSION)

  registerStandardCompletionForDefinitionKeywordNotTopLevelInScala3(new definitions.DefOrTypeFilter, DEF, TYPE)
  registerStandardCompletionForDefinitionKeywordNotTopLevelInScala3(new definitions.DefinitionsFilter, VAL, VAR)
  registerStandardCompletionForDefinitionKeywordNotTopLevelInScala3(new definitions.ValueDefinitionFilter, VAL)

  registerStandardCompletionForDefinitionKeyword(new definitions.ExtensionDefFilter, DEF)

  registerStandardCompletionForExpression(new expression.ExpressionFilter, TRUE, FALSE, NULL, NEW, SUPER, THIS)
  registerStandardCompletionForExpression(new expression.StatementFilter, FOR, WHILE, DO, TRY, RETURN, THROW, IF)
  registerStandardCompletionForExpression(new expression.DoFilter, DO)
  registerStandardCompletionForExpression(new expression.DoYieldFilter, DO, YIELD)
  registerStandardCompletionForExpression(new expression.WhileFilter, WHILE)
  registerStandardCompletionForExpression(new expression.CatchFilter, CATCH)
  registerStandardCompletionForExpression(new expression.QuietCatchCaseFilter, CASE)
  registerStandardCompletionForExpression(new expression.FinallyFilter, FINALLY)
  registerStandardCompletionForExpression(new expression.ElseFilter, ELSE)
  registerStandardCompletionForExpression(new expression.YieldFilter, YIELD)
  registerStandardCompletionForExpression(new expression.MatchFilter, MATCH)
  registerStandardCompletionForExpression(new expression.IfFilter, IF)
  registerStandardCompletionForExpression(new expression.ThenFilter, THEN)

  registerStandardCompletion(new other.ExtendsFilter, EXTENDS)
  registerStandardCompletion(new other.WithFilter, WITH)
  registerStandardCompletion(new other.ForSomeFilter, FOR_SOME)
  registerStandardCompletion(new other.DerivesFilter, DERIVES)

  registerFor(Seq(AfterDotFilter, new other.TypeFilter), TYPE)

  registerFor(
    Seq(
      or(AfterDotFilter, not(AfterRightBraceExpectedErrorFilter)),
      new modifiers.GivenImportSelectorFilter
    ),
    GIVEN
  )
}

object ScalaKeywordCompletionContributor {

  private object AfterDotFilter extends ElementFilter {

    override def toString: String = "AfterDotFilter"

    override def isClassAcceptable(hintClass: Class[_]): Boolean = true

    override def isAcceptable(element: Any, context: PsiElement): Boolean =
      element match {
        case psiElement: PsiElement =>
          val prevElement = PsiTreeUtil.prevCodeLeaf(psiElement)
          prevElement != null && prevElement.getNode.getElementType == ScalaTokenTypes.tDOT
        case _ =>
          false
      }
  }

  private abstract class AfterErrorFilter(errorDescription: String) extends ElementFilter {

    override def isClassAcceptable(hintClass: Class[_]): Boolean = true

    override def isAcceptable(element: Any, context: PsiElement): Boolean =
      element match {
        case psiElement: PsiElement =>
          val prevNonWsElement = PsiTreeUtil.prevLeaf(psiElement) match {
            case ws: PsiWhiteSpace => PsiTreeUtil.prevLeaf(ws)
            case el => el
          }
          checkError(prevNonWsElement)
        case _ =>
          false
      }

    private def checkError(element: PsiElement): Boolean = element match {
      case error: PsiErrorElement =>
        error.getErrorDescription == errorDescription
      case _ => false
    }
  }

  // example: don't allow completing anything after `package org.example <caret>`
  private object AfterSemicolonOrNewLineExpectedErrorFilter
    extends AfterErrorFilter(ScalaBundle.message("semi.expected")) {
    override def toString: String = "AfterSemicolonOrNewLineExpectedErrorFilter"
  }

  // example: don't allow completing anything after `import Foo.{bar <caret>}`
  private object AfterRightBraceExpectedErrorFilter
    extends AfterErrorFilter(ScalaBundle.message("right.brace.expected")) {
    override def toString: String = "AfterRightBraceExpectedErrorFilter"
  }

  private def or(filter: ElementFilter*): OrFilter = new OrFilter(filter: _*)
  private def not(filter: ElementFilter): NotFilter = new NotFilter(filter)
}
