package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementPresentation}
import com.intellij.psi.PsiElement
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScArgumentExprList, ScBlockExpr}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api, result}

final class CaseClauseCompletionContributor extends ScalaCompletionContributor {

  import CaseClauseCompletionContributor._

  extend[ScArgumentExprList](
    new SingleClauseCompletionProvider[ScBlockExpr] {

      override protected def targetType(block: ScBlockExpr): Option[ScType] =
        block.expectedType().collect {
          case api.PartialFunctionType(_, targetType) => targetType
          case api.FunctionType(_, Seq(targetType)) => targetType
        }

      override protected def createLookupElement(patternText: String,
                                                 components: ClassPatternComponents[_])
                                                (implicit place: PsiElement): LookupElement =
        buildLookupElement(
          ScalaKeyword.CASE + patternText,
          new CaseClauseInsertHandler(components)
        ) {
          case (_, presentation: LookupElementPresentation) =>
            presentation.setItemText(ScalaKeyword.CASE)
            presentation.setItemTextBold(true)

            presentation.setTailText(" ")
            presentation.appendTailTextItalic(patternText, false)
        }
    }
  )

  extend[ScCaseClause](
    new SingleClauseCompletionProvider[ScStableReferencePattern] {

      override protected def targetType(pattern: ScStableReferencePattern): Option[ScType] =
        pattern.expectedType

      override protected def findInheritors(definition: ScTypeDefinition): Some[Inheritors] =
        super.findInheritors(definition) match {
          case Some(value) => Some(value)
          case _ => Some(Inheritors(Seq(definition)))
        }

      override protected def createLookupElement(patternText: String,
                                                 components: ClassPatternComponents[_])
                                                (implicit place: PsiElement): LookupElement =
        buildLookupElement(
          patternText,
          new PatternInsertHandler(patternText, components)
        ) {
          case (_, presentation: LookupElementPresentation) =>
            presentation.setItemText(patternText)
            presentation.setItemTextItalic(true)
        }
    }
  )

  extend[ScCaseClause](
    new CompletionProvider[CompletionParameters] {

      override def addCompletions(parameters: CompletionParameters,
                                  context: ProcessingContext,
                                  resultSet: CompletionResultSet): Unit = for {
        ExtractorCompletionProvider(provider) <- positionFromParameters(parameters).parentOfType(classOf[ScReferencePattern]).toSeq

        provider <- AotCompletionProvider :: provider :: Nil
      } provider.addCompletions(parameters, context, resultSet)
    }
  )

  private def extend[
    Capture <: ScalaPsiElement : reflect.ClassTag
  ](provider: CompletionProvider[CompletionParameters]): Unit =
    extend(CompletionType.BASIC, inside[Capture], provider)

}

object CaseClauseCompletionContributor {

  import ExhaustiveMatchCompletionContributor.PatternGenerationStrategy._
  import ScalaPsiElementFactory.createPatternFromTextWithContext

  private abstract class SingleClauseCompletionProvider[
    T <: ScalaPsiElement with result.Typeable : reflect.ClassTag
  ] extends ClauseCompletionProvider[T] {

    override final protected def addCompletions(typeable: T, result: CompletionResultSet)
                                               (implicit place: PsiElement): Unit = for {
      api.ExtractClass(typeDefinition: ScTypeDefinition) <- targetType(typeable).toSeq
      Inheritors(namedInheritors, _) <- findInheritors(typeDefinition)

      inheritor <- namedInheritors
      components = inheritor match {
        case scalaObject: ScObject => new StablePatternComponents(scalaObject)
        case SyntheticExtractorPatternComponents(components) => components
        case PhysicalExtractorPatternComponents(components) => components
        case definition => new TypedPatternComponents(definition)
      }
      lookupElement = createLookupElement(
        components.presentablePatternText(),
        components
      )
    } result.addElement(lookupElement)

    protected def targetType(typeable: T): Option[ScType]

    protected def findInheritors(definition: ScTypeDefinition): Option[Inheritors] =
      SealedDefinition.unapply(definition)

    protected def createLookupElement(patternText: String,
                                      components: ClassPatternComponents[_])
                                     (implicit place: PsiElement): LookupElement
  }

  private final object AotCompletionProvider extends aot.CompletionProvider[ScTypedPattern] {

    override protected def findTypeElement(pattern: ScTypedPattern): Option[ScalaPsiElement] =
      pattern.typePattern.map(_.typeElement)

    override protected def createConsumer(resultSet: CompletionResultSet, position: PsiElement): aot.Consumer = new aot.TypedConsumer(resultSet)

    override protected def createElement(text: String, context: PsiElement, child: PsiElement): ScTypedPattern =
      createPatternFromTextWithContext(text, context, child).asInstanceOf[ScTypedPattern]
  }

  private final class CaseClauseInsertHandler(components: ClassPatternComponents[_])
                                             (implicit place: PsiElement)
    extends ClauseInsertHandler[ScCaseClause] {

    override protected def handleInsert(implicit context: InsertionContext): Unit = {
      replaceText(components.canonicalClauseText)

      onTargetElement { clause =>
        adjustTypesOnClauses(addImports = false, (clause, components))

        reformatAndMoveCaret(clause.getParent.asInstanceOf[ScCaseClauses])(clause)
      }
    }
  }

  private final class PatternInsertHandler(patternText: String,
                                           components: ClassPatternComponents[_])
    extends ClauseInsertHandler[ScConstructorPattern] {

    override def handleInsert(implicit context: InsertionContext): Unit = {
      replaceText(patternText)

      onTargetElement { pattern =>
        adjustTypes(false, (pattern, components)) {
          case pattern@ScParenthesisedPattern(ScTypedPattern(typeElement)) => pattern -> typeElement
        }
      }
    }
  }

  /**
   * Enable completion for object with unapply/unapplySeq methods on case label position.
   * Case label with low letter treat as ScReferencePattern and don't handle with ScalaBasicCompletionContributor,
   * this handler add open and closed brackets to treat element as ScCodeReferenceElement
   * and run ScalaBasicCompletionContributor.
   */
  private final class ExtractorCompletionProvider(pattern: ScPattern) extends DelegatingCompletionProvider[ScPattern] {

    override protected def addCompletions(resultSet: CompletionResultSet,
                                          prefix: String)
                                         (implicit parameters: CompletionParameters,
                                          context: ProcessingContext): Unit = {
      val replacement = createElement("".parenthesize(), prefix, pattern)
      val newParameters = createParameters(replacement)
      resultSet.runRemainingContributors(newParameters, createConsumer(resultSet, pattern))
    }

    override protected def createElement(text: String, context: PsiElement, child: PsiElement): ScPattern =
      createPatternFromTextWithContext(text, context, child)

    override protected def createConsumer(resultSet: CompletionResultSet, position: PsiElement): Consumer[CompletionResult] =
      (result: CompletionResult) => {
        val lookupElement = result.getLookupElement

        val extractorExists = lookupElement.getPsiElement match {
          case Extractor(_) => true
          case _ => false
        }

        if (extractorExists) resultSet.consume(lookupElement)
      }
  }


  private object ExtractorCompletionProvider {

    def unapply(pattern: ScReferencePattern): Option[DelegatingCompletionProvider[ScPattern]] = pattern.getParent match {
      case _: ScNamingPattern | _: ScTypedPattern => None
      case _ => Some(new ExtractorCompletionProvider(pattern))
    }
  }

}