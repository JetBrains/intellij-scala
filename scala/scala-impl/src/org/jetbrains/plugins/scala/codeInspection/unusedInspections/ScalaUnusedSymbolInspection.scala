package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi._
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.annotator.usageTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.codeInspection.unusedInspections.ScalaUnusedSymbolInspection._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{isLocalOrPrivate, superValsSignatures}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerators, ScFunctionExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction, ScFunctionDeclaration, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScalaOverridingMemberSearcher
import org.jetbrains.plugins.scala.util.ScalaMainMethodUtil

class ScalaUnusedSymbolInspection extends HighlightingPassInspection {
  override def isEnabledByDefault: Boolean = true

  override def getDisplayName: String = ScalaInspectionBundle.message("display.name.unused.symbol")

  private def isElementUsed(element: ScNamedElement, isOnTheFly: Boolean): Boolean = {
    if (isOnTheFly) {
      //we can trust RefCounter because references are counted during highlighting
      val refCounter = ScalaRefCountHolder(element)
      var used = false

      val success = refCounter.retrieveUnusedReferencesInfo { () =>
        used |= refCounter.isValueReadUsed(element) || refCounter.isValueWriteUsed(element)
      }

      !success || used //want to return true if it was a failure
    } else {
      //need to look for references because file is not highlighted
      ReferencesSearch.search(element, element.getUseScope).findFirst() != null
    }
  }

  override def invoke(element: PsiElement, isOnTheFly: Boolean): Seq[ProblemInfo] = if (!shouldProcessElement(element)) Seq.empty else {
    val elements: Seq[PsiElement] = element match {
      case fun: ScFunctionExpr => fun.parameters.filterNot(p => p.isWildcard || p.isImplicitParameter)
      case fun: ScMethodLike =>
        val funIsPublic = !fun.containingClass.toOption.exists(isLocalOrPrivate)
        def nonPrivateClassMemberParam(param: ScParameter): Boolean =
          funIsPublic && param.asOptionOf[ScClassParameter].exists(p => p.isClassMember && (!p.isPrivate))
        def overridingParam(param: ScParameter): Boolean =
          param.asOptionOf[ScClassParameter].exists(isOverridingOrOverridden)
        def caseClassParam(param: ScParameter): Boolean =
          param.asOptionOf[ScClassParameter].exists(_.isCaseClassVal)
        isLocalOrPrivate(fun).option(fun) ++:
          fun.parameters
          .filterNot(_.isWildcard)
          .filter(_.isPhysical)   // context bound are desugared into parameters, for example
          .filterNot(_.isImplicitParameter)
          .filterNot(caseClassParam)
          .filterNot(nonPrivateClassMemberParam)
          .filterNot(overridingParam)
      case caseClause: ScCaseClause => caseClause.pattern.toSeq.flatMap(_.bindings).filterNot(_.isWildcard)
      case declaredHolder: ScDeclaredElementsHolder => declaredHolder.declaredElements.filterNot(isOverridingOrOverridden)
      case enumerators: ScEnumerators => enumerators.patterns.flatMap(_.bindings).filterNot(_.isWildcard) //for statement
      case _ => Seq.empty
    }
    elements.flatMap {
      case holder: PsiAnnotationOwner if hasUnusedAnnotation(holder) => Seq.empty
      case named: ScNamedElement =>
        if (!isElementUsed(named, isOnTheFly)) {
          Seq(ProblemInfo(named.nameId, ScalaUnusedSymbolInspection.annotationDescription, ProblemHighlightType.LIKE_UNUSED_SYMBOL, DeleteUnusedElementFix.quickfixesFor(named)))
        } else Seq.empty
      case _ => Seq.empty
    }
  }

  override def shouldProcessElement(elem: PsiElement): Boolean = elem match {
    case m: ScMember if m.hasModifierPropertyScala(ScalaKeyword.IMPLICIT) => false
    case _: ScFunctionDeclaration => false
    case p: ScModifierListOwner if hasOverrideModifier(p) => false
    case fd: ScFunctionDefinition if ScalaMainMethodUtil.isMainMethod(fd) => false
    case f: ScFunction if f.isSpecial || isOverridingFunction(f) => false
    case _: ScMethodLike => true // handle in invoke
    case _ => isLocalOrPrivate(elem)
  }
}

object ScalaUnusedSymbolInspection {
  @Nls
  val annotationDescription: String = ScalaInspectionBundle.message("declaration.is.never.used")

  val shortName: String = "ScalaUnusedSymbol"

  private def hasOverrideModifier(member: ScModifierListOwner): Boolean =
    member.hasModifierPropertyScala(ScalaModifier.OVERRIDE)

  private def isOverridingOrOverridden(element: PsiNamedElement): Boolean =
    superValsSignatures(element, withSelfType = true).nonEmpty || isOverridden(element)

  private def isOverridingFunction(func: ScFunction): Boolean =
    hasOverrideModifier(func) || func.superSignatures.nonEmpty || isOverridden(func)

  private def isOverridden(member: PsiNamedElement): Boolean =
    ScalaOverridingMemberSearcher.search(member, deep = false, withSelfType = true).nonEmpty

  private def hasUnusedAnnotation(holder: PsiAnnotationOwner): Boolean =
    holder.hasAnnotation("scala.annotation.unused") ||
      // not entirely correct, but if we find @nowarn here in this situation
      // we can assume that it is directed at the unusedness of the symbol
      holder.hasAnnotation("scala.annotation.nowarn")
}
