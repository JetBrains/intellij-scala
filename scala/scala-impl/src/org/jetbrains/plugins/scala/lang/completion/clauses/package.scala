package org.jetbrains.plugins.scala
package lang
package completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementBuilder, LookupElementRenderer}
import com.intellij.patterns.{ElementPattern, PlatformPatterns, PsiElementPattern, StandardPatterns}
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.DirectClassInheritorsSearch
import com.intellij.psi.{PsiAnonymousClass, PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster.adjustFor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScPattern, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScSimpleTypeElement, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createPatternFromTextWithContext
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, PartialFunctionType}

package object clauses {

  /**
   * [[ClauseCompletionParameters]] is supposed to be used in places
   * where an actual [[CompletionParameters]] cannot be instantiated.
   *
   * It encapsulates information on:
   * 1) completion position;
   * 2) original file resolve scope;
   *
   * Either invocation count or completion type information might be used eventually.
   */
  case class ClauseCompletionParameters(place: PsiElement,
                                        scope: GlobalSearchScope)

  import PlatformPatterns.psiElement
  import PsiElementPattern.Capture

  private[clauses] def insideCaseClause: Capture[PsiElement] =
    psiElement.inside(classOf[ScCaseClause])

  private[clauses] def leaf: Capture[LeafPsiElement] =
    psiElement(classOf[LeafPsiElement])

  private[clauses] def referenceWithParent(patterns: ElementPattern[_ <: PsiElement]*): Capture[LeafPsiElement] =
    leaf.withParent(classOf[ScReferenceExpression])
      .withSuperParent(2, StandardPatterns.or(patterns: _*))

  private[clauses] def adjustTypesOnClauses(addImports: Boolean,
                                            pairs: (ScCaseClause, PatternComponents)*): Unit =
    adjustTypes(addImports, pairs: _*) {
      case ScCaseClause(Some(pattern@ScTypedPattern(typeElement)), _, _) => pattern -> typeElement
    }

  private[clauses] def adjustTypes[E <: ScalaPsiElement](addImports: Boolean,
                                                         pairs: (E, PatternComponents)*)
                                                        (collector: PartialFunction[E, (ScPattern, ScTypeElement)]): Unit = {
    val findTypeElement = collector.lift
    adjustFor(
      for {
        (element, _) <- pairs
        (_, typeElement) <- findTypeElement(element)
      } yield typeElement,
      addImports = addImports,
      useTypeAliases = false
    )

    for {
      (element, components: ClassPatternComponents) <- pairs
      (pattern, ScSimpleTypeElement.unwrapped(codeReference)) <- findTypeElement(element)

      replacement = createPatternFromTextWithContext(
        components.presentablePatternText(Right(codeReference)),
        pattern.getContext,
        pattern
      )
    } pattern.replace(replacement)
  }

  private[clauses] def expectedMatchType(`match`: ScMatch) =
    `match`.expression.flatMap(_.`type`().toOption)

  private[clauses] def expectedFunctionalType(block: ScBlockExpr) = block.expectedType().collect {
    case PartialFunctionType(_, targetType) => targetType
    case FunctionType(_, Seq(targetType)) => targetType
  }

  private[clauses] def buildLookupElement(lookupString: String,
                                          insertHandler: ClauseInsertHandler[_])
                                         (presentation: LookupElementRenderer[LookupElement]): LookupElement =
    LookupElementBuilder.create(lookupString)
      .withInsertHandler(insertHandler)
      .withRenderer(presentation)

  private[clauses] case class Inheritors(namedInheritors: List[PsiClass],
                                         isExhaustive: Boolean) {

    if (namedInheritors.isEmpty) throw new IllegalArgumentException("Class contract violation")
  }

  private[clauses] object DirectInheritors {

    def unapply(`class`: PsiClass)
               (implicit parameters: ClauseCompletionParameters): Option[Inheritors] = {
      val isSealed = `class`.isSealed
      val (namedInheritors, anonymousInheritors) = directInheritors(`class`).partition {
        case _: ScNewTemplateDefinition |
             _: PsiAnonymousClass => false
        case _ => true
      }

      implicit val ordered: Ordering[PsiClass] =
        if (isSealed) Ordering.by(_.getNavigationElement.getTextRange.getStartOffset)
        else Ordering.by(_.getName)

      namedInheritors.sorted.toList match {
        case Nil => None
        case inheritors =>
          val isNotConcrete = `class` match {
            case scalaClass: ScClass => scalaClass.hasAbstractModifier
            case _ => true
          }

          val isExhaustive = isSealed && isNotConcrete && anonymousInheritors.isEmpty
          Some(Inheritors(inheritors, isExhaustive))
      }
    }

    private def directInheritors(`class`: PsiClass)
                                (implicit parameters: ClauseCompletionParameters) = {
      import collection.JavaConverters._
      DirectClassInheritorsSearch
        .search(`class`, parameters.scope)
        .findAll()
        .asScala
        .toIndexedSeq
    }
  }

  private[clauses] object Extractor {

    def unapply(`object`: ScObject): Option[ScFunctionDefinition] = `object`.membersWithSynthetic.collectFirst {
      case function: ScFunctionDefinition if function.isUnapplyMethod => function
    }
  }
}
