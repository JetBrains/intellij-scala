package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.{ChangeInfo, JavaChangeInfo, OverriderUsageInfo}
import com.intellij.util.containers.MultiMap
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.types.api.ValType
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.changeInfo.ScalaChangeInfo

/**
 * Nikolay.Tropin
 * 2014-08-13
 */
private[changeSignature] object ConflictsUtil {

  type ConflictsMap = MultiMap[PsiElement, String]

  def addJavaOverriderConflicts(info: OverriderUsageInfo, change: ChangeInfo, map: ConflictsMap): Unit = {
    change match {
      case sc: ScalaChangeInfo if sc.newParameters.exists(p => p.isByName && p.scType.isInstanceOf[ValType]) =>
        val message = ScalaBundle.message("by.name.parameters.cannot.be.used")
        map.putValue(info.getOverridingMethod, message)
      case _ =>
    }
  }

  def addBindingPatternConflicts(bp: ScBindingPattern,
                                         change: ChangeInfo,
                                         result: ConflictsMap): Unit = {

    if (change.getNewParameters.nonEmpty) {
      val (member: ScMember, kind, isSimple) = bp match {
        case ScalaPsiUtil.inNameContext(pd: ScPatternDefinition) => (pd, "pattern definition", pd.isSimple)
        case ScalaPsiUtil.inNameContext(vd: ScVariableDefinition) => (vd, "variable definition", vd.isSimple)
        case _ => return
      }

      if (!isSimple) {
        val className = member.containingClass.qualifiedName
        val message = ScalaBundle.message("method.is.overridden.in.composite.kind.in.class", kind, className)

        result.putValue(bp, message)
      }
    }
  }

  def addClassParameterConflicts(cp: ScClassParameter, change: ChangeInfo, result: ConflictsMap): Unit = {
    if (change.getNewParameters.nonEmpty) {
      val className = cp.containingClass.qualifiedName
      val message = ScalaBundle.message("method.is.overridden.by.class.parameter.of.class", className)
      result.putValue(cp, message)
    }
  }

  def addUnapplyUsagesConflicts(p: PatternUsageInfo, change: ChangeInfo, result: ConflictsMap): Unit = {
    change match {
      case jc: JavaChangeInfo if jc.isParameterSetOrOrderChanged || jc.isParameterTypesChanged =>
        jc.getMethod match {
          case ScPrimaryConstructor.ofClass(clazz) if clazz.isCase =>
            val message = ScalaBundle.message("updating.of.usages.of.generated.unapply")
            result.putValue(p.pattern, message)
          case _ =>
        }
      case _ =>
    }
  }
}
