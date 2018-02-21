package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.psi._
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.scala.annotator.usageTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScEnumerators, ScFunctionExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

class ScalaUnusedSymbolInspection extends HighlightingPassInspection {
  override def isEnabledByDefault: Boolean = true

  override def getDisplayName: String = "Unused Symbol"

  private def isElementUsed(elem: ScNamedElement, isOnTheFly: Boolean): Boolean = {
    if (isOnTheFly) {
      //we can trust RefCounter because references are counted during highlighting
      val refCounter = ScalaRefCountHolder.getInstance(elem.getContainingFile)
      var used = false
      val success = refCounter.retrieveUnusedReferencesInfo { () =>
        if (refCounter.isValueUsed(elem)) {
          used = true
        }
      }
      !success || used //want to return true if it was a failure
    } else {
      //need to look for references because file is not highlighted
      ReferencesSearch.search(elem, elem.getUseScope).findFirst() != null
    }
  }

  override def invoke(element: PsiElement, isOnTheFly: Boolean): Seq[ProblemInfo] = if (!shouldProcessElement(element)) Seq.empty else {
    val elements: Seq[PsiElement] = element match {
      case caseClause: ScCaseClause => caseClause.pattern.toSeq.flatMap(_.bindings).filterNot(_.isWildcard)
      case declaredHolder: ScDeclaredElementsHolder => declaredHolder.declaredElements
      case fun: ScFunctionExpr => fun.parameters.filterNot(p => p.isWildcard || p.isImplicitParameter)
      case enumerators: ScEnumerators => enumerators.patterns.flatMap(_.bindings).filterNot(_.isWildcard) //for statement
      case _ => Seq.empty
    }
    elements.flatMap {
      case named: ScNamedElement =>
        if (!isElementUsed(named, isOnTheFly)) {
          Seq(ProblemInfo(named.nameId, ScalaUnusedSymbolInspection.Annotation, Seq(new DeleteUnusedElementFix(named))))
        } else Seq.empty
      case _ => Seq.empty
    }
  }

  def shouldProcessElement(elem: PsiElement): Boolean = elem match {
    case f: ScFunction if f.isSpecial => false
    case m: ScMember if m.hasModifierProperty(ScalaKeyword.IMPLICIT) => false
    case _ => ScalaPsiUtil.isLocalOrPrivate(elem)
  }
}

object ScalaUnusedSymbolInspection {
  val Annotation = "Declaration is never used"

  val ShortName: String = "ScalaUnusedSymbol"
}
