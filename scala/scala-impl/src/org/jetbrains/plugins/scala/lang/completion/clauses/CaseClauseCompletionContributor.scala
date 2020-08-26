package org.jetbrains.plugins.scala
package lang
package completion
package clauses

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementPresentation}
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.{ElementPattern, PlatformPatterns}
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.util.{Consumer, ProcessingContext}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createPatternFromTextWithContext
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

final class CaseClauseCompletionContributor extends ScalaCompletionContributor {

  import CaseClauseCompletionContributor._
  import PlatformPatterns.psiElement

  extend(
    leafWithParent {
      `match` ||
        nonQualifiedReference.withParents(classOf[ScBlock], classOf[ScCaseClause], classOf[ScCaseClauses], classOf[ScMatch])
    }
  ) {
    new SingleClauseCompletionProvider[ScMatch] {

      override protected def targetType(`match`: ScMatch): Option[ScType] =
        expectedMatchType(`match`)
    }
  }

  extend(
    leafWithParent {
      nonQualifiedReference.withParent {
        psiElement(classOf[ScBlockExpr]) ||
          psiElement(classOf[ScBlock]).withParent(classOf[ScCaseClause])
      }
    }
  ) {
    new SingleClauseCompletionProvider[ScBlockExpr] {

      override protected def targetType(block: ScBlockExpr): Option[ScType] =
        expectedFunctionalType(block)
    }
  }

  extend(insideCaseClause) {
    new SingleClauseCompletionProvider[ScStableReferencePattern] {

      override protected def targetType(pattern: ScStableReferencePattern): Option[ScType] =
        pattern.expectedType

      override protected def findTargetDefinitions(`class`: PsiClass)
                                                  (implicit parameters: ClauseCompletionParameters): List[PsiClass] =
        super.findTargetDefinitions(`class`) match {
          case Nil => `class` :: Nil
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

  extend(insideCaseClause) {
    new CompletionProvider[CompletionParameters] {

      override def addCompletions(parameters: CompletionParameters,
                                  context: ProcessingContext,
                                  resultSet: CompletionResultSet): Unit = for {
        ExtractorCompletionProvider(provider) <- positionFromParameters(parameters).parentOfType(classOf[ScReferencePattern]).toSeq

        provider <- AotCompletionProvider :: provider :: Nil
      } provider.addCompletions(parameters, context, resultSet)
    }
  }

  private def extend(place: ElementPattern[_ <: PsiElement])
                    (provider: CompletionProvider[CompletionParameters]): Unit =
    extend(CompletionType.BASIC, place, provider)

}

object CaseClauseCompletionContributor {

  private abstract class SingleClauseCompletionProvider[
    T <: ScalaPsiElement with Typeable : reflect.ClassTag
  ] extends ClauseCompletionProvider[T] {

    override final protected def addCompletions(typeable: T, result: CompletionResultSet)
                                               (implicit parameters: ClauseCompletionParameters): Unit = for {
      scType <- targetType(typeable).toList
      targetClass <- scType.extractClass

      components <- createComponents(scType, targetClass)

      lookupElement = createLookupElement(
        components.presentablePatternText(),
        components
      )(parameters.place)
    } result.addElement(lookupElement)

    protected def targetType(typeable: T): Option[ScType]

    protected def findTargetDefinitions(`class`: PsiClass)
                                       (implicit parameters: ClauseCompletionParameters): List[PsiClass] =
      `class` match {
        case DirectInheritors(Inheritors(namedInheritors, _, _)) => namedInheritors
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

    private def createComponents(`type`: ScType, `class`: PsiClass)
                                (implicit parameters: ClauseCompletionParameters): List[ClassPatternComponents] =
      (`type`, `class`) match {
        case (TupleType(types), tupleClass: ScClass) =>
          new TuplePatternComponents(tupleClass, types) :: Nil
        case _ =>
          findTargetDefinitions(`class`).map {
            case scalaObject: ScObject => new StablePatternComponents(scalaObject)
            case CaseClassPatternComponents(components) => components
            case PhysicalExtractorPatternComponents(components) => components
            case psiClass => new TypedPatternComponents(psiClass)
          }
      }
  }

  private final object AotCompletionProvider extends aot.CompletionProvider[ScTypedPattern] {

    override protected def findTypeElement(pattern: ScTypedPattern): Option[ScTypeElement] =
      pattern.typePattern.map(_.typeElement)

    override protected def createConsumer(resultSet: CompletionResultSet, position: PsiElement): aot.Consumer = new aot.TypedConsumer(resultSet)

    override protected def createElement(text: String, context: PsiElement, child: PsiElement): ScTypedPattern =
      createPatternFromTextWithContext(text, context, child).asInstanceOf[ScTypedPattern]
  }

  private final class CaseClauseInsertHandler(components: ClassPatternComponents)
    extends ClauseInsertHandler[ScCaseClause] {

    override protected def handleInsert(implicit context: InsertionContext): Unit = {
      replaceText(components.canonicalClauseText)

      onTargetElement { clause =>
        adjustTypesOnClauses(addImports = true, List((clause, components)))

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
        adjustTypes(false, List((pattern, components))) {
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