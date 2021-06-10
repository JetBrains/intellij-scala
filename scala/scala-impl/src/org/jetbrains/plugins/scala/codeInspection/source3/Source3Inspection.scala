package org.jetbrains.plugins.scala.codeInspection.source3

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.source3.Source3Inspection._
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractRegisteredInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScConstructorPattern, ScNamingPattern, ScSeqWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScCompoundTypeElement, ScWildcardTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFor, ScGenerator, ScMethodCall, ScTypedExpression}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector, ScImportSelectors, ScImportStmt}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

class Source3Inspection extends AbstractRegisteredInspection {
  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           @Nls descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    if (!element.getContainingFile.isSource3Enabled) {
      return None
    }

    element match {
      case ScWildcardTypeElementUnderscore(wildcardTypeElement, underscore) =>
        super.problemDescriptor(
          underscore,
          createReplacingQuickFix(wildcardTypeElement, ScalaInspectionBundle.message("replace.with.questionmark")) { e =>
            ScalaPsiElementFactory.createTypeElementFromText(e.getText.replaceFirst("_", "?"), e, null)
          }
        )
      case gen@ScGenerator(pattern, Some(expr)) if gen.caseKeyword.isEmpty && !pattern.isIrrefutableFor(expr.`type`().toOption)=>
        super.problemDescriptor(
          pattern,
          createReplacingQuickFix(gen, ScalaInspectionBundle.message("add.case")) { gen =>
            ScalaPsiElementFactory.createExpressionFromText(s"for { case ${gen.getText} } ()")(gen)
              .asInstanceOf[ScFor].enumerators.head.generators.head
          }
        )
      case ElementType(ScalaTokenTypes.tUNDER) if element.getParent.is[/*ScImportSelector TODO: this is correct but the scala compiler has a bug in 2.13.6*/ ScImportExpr] &&
                                                  element.prevSibling.forall(_.elementType == ScalaTokenTypes.tDOT) =>
        super.problemDescriptor(
          element,
          createReplacingQuickFix(element, ScalaInspectionBundle.message("replace.with.star")) { underscore =>
            ScalaPsiElementFactory.createImportFromTextWithContext("import a.*", underscore.getContext, null).lastLeaf
          }
        )
      case ElementType(ScalaTokenTypes.tFUNTYPE) if element.getParent.is[ScImportSelector] =>
        super.problemDescriptor(
          element,
          createReplacingQuickFix(element, ScalaInspectionBundle.message("replace.with.as")) { arrow =>
            ScalaPsiElementFactory.createImportFromTextWithContext("import a.{x as y}", arrow.getContext, null)
              .importExprs.head.selectors.head.findFirstChildByType(ScalaTokenType.AsKeyword).get
          }
        )
      case typed@ScTypedExpression.sequenceArg(seqArg) if seqArg.getFirstChild.elementType == ScalaTokenTypes.tUNDER =>
        super.problemDescriptor(
          seqArg,
          createReplacingQuickFix(typed, ScalaInspectionBundle.message("replace.with.star")) { typed =>
            ScalaPsiElementFactory.createExpressionWithContextFromText(s"call(${typed.expr.getText}*)" , typed.getContext, typed)
              .asInstanceOf[ScMethodCall].argumentExpressions.head
          }
        )
      case named@ScNamingPattern(seqWildcard: ScSeqWildcardPattern) if seqWildcard.isWildcard =>
        super.problemDescriptor(
          named,
          createReplacingQuickFix(named, ScalaInspectionBundle.message("replace.with.name.followed.by.star", named.name)) { named =>
            ScalaPsiElementFactory.createPatternFromTextWithContext(s"Seq(${named.name}*)", named.getContext, named)
              .asInstanceOf[ScConstructorPattern].args.patterns.head
          }
        )
      case withKw@ElementType(ScalaTokenTypes.kWITH) && Parent(_: ScCompoundTypeElement) =>
        super.problemDescriptor(
          withKw,
          createReplacingQuickFix(withKw, ScalaInspectionBundle.message("replace.with.and.char")) { withKw =>
            ScalaPsiElementFactory.createIdentifier("&")(withKw).getPsi
          }
        )
      case _ => None
    }
  }
}

object Source3Inspection {
  //private val `.getText`: Qualified = invocation("getText").from(ArraySeq(psiElementFqn, psiASTNodeFqn))
  //private val `.contains`: Qualified = invocation("contains")

  object ScWildcardTypeElementUnderscore {
    def unapply(wildcardType: ScWildcardTypeElement): Option[(ScWildcardTypeElement, PsiElement)] =
      wildcardType.findFirstChildByType(ScalaTokenTypes.tUNDER).map(wildcardType -> _)
  }

  // token '=>' and '_' (but only when not used for shadowing)
  //  import base.{something => _, _}
  //                         ^^    ^
  //                            ^ <- not this
  object UpgradableImportToken {
    def unapply(e: PsiElement): Option[(PsiElement, ScImportStmt)] = {
      def elementIfInImport = e.parents.findByType[ScImportStmt].map(e -> _)
      e match {
        case ElementType(ScalaTokenTypes.tFUNTYPE) => elementIfInImport
        case ElementType(ScalaTokenTypes.tFUNTYPE) if e.getParent.is[ScImportSelectors] => elementIfInImport
        case _ => None
      }
    }
  }

  private def createReplacingQuickFix[T <: PsiElement](element: T, @Nls name: String)(transform: T => PsiElement): Some[LocalQuickFix] =
    Some(new AbstractFixOnPsiElement(name: String, element) {
      override protected def doApplyFix(element: T)(implicit project: Project): Unit = {
        element.replace(transform(element))
      }
    })
}