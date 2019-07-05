package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementPresentation}
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createPatternFromTextWithContext
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

final class CaseClauseCompletionContributor extends ScalaCompletionContributor {

  import CaseClauseCompletionContributor._

  extend(
    psiElement(classOf[LeafPsiElement])
      .withParent(
        psiElement(classOf[ScMatch]) ||
          psiElement(classOf[ScReferenceExpression]).withParents(classOf[ScBlock], classOf[ScCaseClause], classOf[ScCaseClauses], classOf[ScMatch])
      )
  ) {
    new SingleClauseCompletionProvider[ScMatch] {

      override protected def targetType(`match`: ScMatch): Option[ScType] =
        `match`.expression.flatMap(_.`type`().toOption)
    }
  }

  extend(
    psiElement(classOf[LeafPsiElement])
      .withParent(classOf[ScReferenceExpression])
      .withSuperParent(
        2,
        psiElement(classOf[ScBlockExpr]).withParent(psiElement(classOf[ScArgumentExprList]) || psiElement(classOf[ScInfixExpr])) ||
          psiElement(classOf[ScBlock]).withParent(classOf[ScCaseClause])
      )
  ) {
    new SingleClauseCompletionProvider[ScBlockExpr] {

      override protected def targetType(block: ScBlockExpr): Option[ScType] =
        block.expectedType().collect {
          case PartialFunctionType(_, targetType) => targetType
          case FunctionType(_, Seq(targetType)) => targetType
        }
    }
  }

  extend() {
    new SingleClauseCompletionProvider[ScStableReferencePattern] {

      override protected def targetType(pattern: ScStableReferencePattern): Option[ScType] =
        pattern.expectedType

      override protected def findTargetDefinitions(definition: ScTypeDefinition): List[ScTypeDefinition] =
        super.findTargetDefinitions(definition) match {
          case Nil => definition :: Nil
          case list => list
        }

      override protected def createLookupElement(patternText: String,
                                                 components: ClassPatternComponents)
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
  }

  extend() {
    new CompletionProvider[CompletionParameters] {

      override def addCompletions(parameters: CompletionParameters,
                                  context: ProcessingContext,
                                  resultSet: CompletionResultSet): Unit = for {
        ExtractorCompletionProvider(provider) <- positionFromParameters(parameters).parentOfType(classOf[ScReferencePattern]).toSeq

        provider <- AotCompletionProvider :: provider :: Nil
      } provider.addCompletions(parameters, context, resultSet)
    }
  }

  private def extend(place: ElementPattern[_ <: PsiElement] = inside[ScCaseClause])
                    (provider: CompletionProvider[CompletionParameters]): Unit =
    extend(CompletionType.BASIC, place, provider)

}

object CaseClauseCompletionContributor {

  import ExhaustiveMatchCompletionContributor.PatternGenerationStrategy._

  private abstract class SingleClauseCompletionProvider[
    T <: ScalaPsiElement with Typeable : reflect.ClassTag
  ] extends ClauseCompletionProvider[T] {

    override final protected def addCompletions(typeable: T, result: CompletionResultSet)
                                               (implicit place: PsiElement): Unit = for {
      scType@ExtractClass(typeDefinition: ScTypeDefinition) <- targetType(typeable).toList
      components <- createComponents(scType, typeDefinition)

      lookupElement = createLookupElement(
        components.presentablePatternText(),
        components
      )
    } result.addElement(lookupElement)

    protected def targetType(typeable: T): Option[ScType]

    protected def findTargetDefinitions(definition: ScTypeDefinition): List[ScTypeDefinition] =
      definition match {
        case DirectInheritors(Inheritors(namedInheritors, _)) => namedInheritors
        case _ => Nil
      }

    protected def createLookupElement(patternText: String,
                                      components: ClassPatternComponents)
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

    private def createComponents(`type`: ScType, typeDefinition: ScTypeDefinition)
                                (implicit place: PsiElement): List[ClassPatternComponents] =
      (`type`, typeDefinition) match {
        case (TupleType(types), tupleClass: ScClass) =>
          new TuplePatternComponents(tupleClass, types) :: Nil
        case _ =>
          findTargetDefinitions(typeDefinition).map {
            case scalaObject: ScObject => new StablePatternComponents(scalaObject)
            case CaseClassPatternComponents(components) => components
            case PhysicalExtractorPatternComponents(components) => components
            case definition => new TypedPatternComponents(definition)
          }
      }
  }

  private final object AotCompletionProvider extends aot.CompletionProvider[ScTypedPattern] {

    override protected def findTypeElement(pattern: ScTypedPattern): Option[ScalaPsiElement] =
      pattern.typePattern.map(_.typeElement)

    override protected def createConsumer(resultSet: CompletionResultSet, position: PsiElement): aot.Consumer = new aot.TypedConsumer(resultSet)

    override protected def createElement(text: String, context: PsiElement, child: PsiElement): ScTypedPattern =
      createPatternFromTextWithContext(text, context, child).asInstanceOf[ScTypedPattern]
  }

  private final class CaseClauseInsertHandler(components: ClassPatternComponents)
                                             (implicit place: PsiElement)
    extends ClauseInsertHandler[ScCaseClause] {

    override protected def handleInsert(implicit context: InsertionContext): Unit = {
      replaceText(components.canonicalClauseText)

      onTargetElement { clause =>
        adjustTypesOnClauses(addImports = false, (clause, components))

        clause.getParent match {
          case clauses: ScCaseClauses =>
            val rangesToReformat = for {
              clause <- clauses.caseClauses
              Some(arrow) = clause.funType
            } yield TextRange.from(clause.getTextOffset, arrow.getStartOffsetInParent)

            reformatAndMoveCaret(clauses, clause, rangesToReformat: _*)
        }
      }
    }
  }

  private final class PatternInsertHandler(patternText: String,
                                           components: ClassPatternComponents)
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