package org.jetbrains.plugins.scala
package codeInspection
package varCouldBeValInspection

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.importsTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.codeInspection.unusedInspections.{HighlightingPassInspection, ProblemInfo}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariableDefinition

class VarCouldBeValInspection extends HighlightingPassInspection {
  override def isEnabledByDefault: Boolean = true

  override def invoke(element: PsiElement): Seq[ProblemInfo] = element match {
    case varDef: ScVariableDefinition =>
      var couldBeVal = true
      var used = false
      varDef.declaredElements.foreach { elem =>
        val holder = ScalaRefCountHolder.getInstance(element.getContainingFile)
        holder.retrieveUnusedReferencesInfo { () =>
          if (holder.isValueWriteUsed(elem)) {
            couldBeVal = false
          }
          if (holder.isValueUsed(elem)) {
            used = true
          }
        }
      }
      if (couldBeVal && used) {
        Seq(ProblemInfo(varDef.varKeyword, VarCouldBeValInspection.Annotation, Seq(new VarToValFix(varDef))))
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
