package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.psi._
import org.jetbrains.plugins.scala.annotator.importsTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

class ScalaUnusedSymbolInspection extends HighlightingPassInspection {
  override def isEnabledByDefault: Boolean = true

  override def getDisplayName: String = "Unused Symbol"

  override def invoke(element: PsiElement): Seq[ProblemInfo] = element match {
    case declaredHolder: ScDeclaredElementsHolder if shouldProcessElement(element) =>
      declaredHolder.declaredElements.flatMap {
        case named: ScNamedElement =>
          val refCounter = ScalaRefCountHolder.getInstance(named.getContainingFile)
          var used = false
          refCounter.retrieveUnusedReferencesInfo { () =>
            if (refCounter.isValueUsed(named)) {
              used = true
            }
          }
          if (!used) {
            Seq(ProblemInfo(named.nameId, ScalaUnusedSymbolInspection.Annotation, Seq(new DeleteUnusedElementFix(named))))
          } else Seq.empty
        case _ => Seq.empty
      }
    case _ => Seq.empty
  }

  def shouldProcessElement(elem: PsiElement): Boolean = elem match {
    case f: ScFunction if ScFunction.isSpecial(f.name) => false
    case m: ScMember if m.hasModifierProperty(ScalaKeyword.IMPLICIT) => false
    case _ => ScalaPsiUtil.isLocalOrPrivate(elem)
  }
}

object ScalaUnusedSymbolInspection {
  val Annotation = "Declaration is never used"

  val ShortName: String = "ScalaUnusedSymbol"
}
