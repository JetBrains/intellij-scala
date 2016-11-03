package org.jetbrains.plugins.scala
package codeInspection
package varCouldBeValInspection

import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import org.jetbrains.plugins.scala.annotator.importsTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.codeInspection.unusedInspections.{HighlightingPassInspection, ProblemInfo}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement

class VarCouldBeValInspection extends HighlightingPassInspection {
  override def isEnabledByDefault: Boolean = true

  private def hasNoWriteUsages(elem: ScNamedElement, isOnTheFly: Boolean): Boolean = {
    if (isOnTheFly) {
      var hasNoWriteUsages = true
      var used = false
      val holder = ScalaRefCountHolder.getInstance(elem.getContainingFile)
      holder.retrieveUnusedReferencesInfo { () =>
        if (holder.isValueWriteUsed(elem)) {
          hasNoWriteUsages = false
        }
        if (holder.isValueUsed(elem)) {
          used = true
        }
      }
      hasNoWriteUsages && used //has no write usages but is used
    } else {
      import scala.collection.JavaConversions._
      val references = ReferencesSearch.search(elem, elem.getUseScope).findAll()
      val hasNoWriteUsages = references.forall {
        !ScalaPsiUtil.isPossiblyAssignment(_)
      }
      hasNoWriteUsages && !references.isEmpty
    }
  }

  override def invoke(element: PsiElement, isOnTheFly: Boolean): Seq[ProblemInfo] = element match {
    case varDef: ScVariableDefinition =>
      var couldBeVal = true
      varDef.declaredElements.foreach { elem =>
        couldBeVal = couldBeVal && hasNoWriteUsages(elem, isOnTheFly)
      }
      if (couldBeVal) {
        Seq(ProblemInfo(varDef.keywordToken, VarCouldBeValInspection.Annotation, Seq(new VarToValFix(varDef))))
      } else Seq.empty
    case _ => Seq.empty
  }

  override def shouldProcessElement(elem: PsiElement): Boolean = elem match {
    case _: ScVariableDefinition => ScalaPsiUtil.isLocalOrPrivate(elem)
    case _ => false
  }
}

object VarCouldBeValInspection {
  val ShortName: String = "VarCouldBeVal"

  val Annotation: String = "var could be a val"
}
