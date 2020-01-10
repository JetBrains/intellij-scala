package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi._
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.scala.annotator.usageTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.codeInspection.unusedInspections.ScalaUnusedSymbolInspection._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScMethodLike
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerators, ScFunctionExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

class ScalaUnusedSymbolInspection extends HighlightingPassInspection {
  override def isEnabledByDefault: Boolean = true

  override def getDisplayName: String = "Unused Symbol"

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
        def nonPrivateClassMemberParam(param: ScParameter): Boolean =
          !ScalaPsiUtil.isLocalOrPrivate(fun.containingClass) &&
            param.asOptionOf[ScClassParameter].exists(p => p.isClassMember && (!p.isPrivate))
        def overridingParam(param: ScParameter): Boolean =
          param.asOptionOf[ScClassParameter].exists(overridesMember)
        ScalaPsiUtil.isLocalOrPrivate(fun).option(fun) ++: fun.parameters.filterNot(_.isWildcard).filterNot(nonPrivateClassMemberParam).filterNot(overridingParam)
      case caseClause: ScCaseClause => caseClause.pattern.toSeq.flatMap(_.bindings).filterNot(_.isWildcard)
      case declaredHolder: ScDeclaredElementsHolder => declaredHolder.declaredElements
      case enumerators: ScEnumerators => enumerators.patterns.flatMap(_.bindings).filterNot(_.isWildcard) //for statement
      case _ => Seq.empty
    }
    elements.flatMap {
      case named: ScNamedElement =>
        if (!isElementUsed(named, isOnTheFly)) {
          Seq(ProblemInfo(named.nameId, ScalaUnusedSymbolInspection.Annotation, ProblemHighlightType.LIKE_UNUSED_SYMBOL, Seq(new DeleteUnusedElementFix(named))))
        } else Seq.empty
      case _ => Seq.empty
    }
  }

  override def shouldProcessElement(elem: PsiElement): Boolean = elem match {
    case f: ScFunction if f.isSpecial => false
    case m: ScMember if m.hasModifierProperty(ScalaKeyword.IMPLICIT) => false
    case _: ScMethodLike => true // handle in invoke
    case _ => ScalaPsiUtil.isLocalOrPrivate(elem)
  }
}

object ScalaUnusedSymbolInspection {
  val Annotation = "Declaration is never used"

  val ShortName: String = "ScalaUnusedSymbol"

  private def overridesMember(element: ScClassParameter): Boolean = {
    import lang.psi.ScalaPsiUtil._
    element.hasModifierProperty(ScalaModifier.OVERRIDE) || superValsSignatures(element).nonEmpty
  }
}
