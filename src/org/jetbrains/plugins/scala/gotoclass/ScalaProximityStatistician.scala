package org.jetbrains.plugins.scala.gotoclass

import com.intellij.psi.PsiElement
import com.intellij.psi.statistics.StatisticsInfo
import com.intellij.psi.util.ProximityLocation
import com.intellij.psi.util.proximity.ProximityStatistician
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable, ScTypeAlias, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScClass, ScObject, ScMember}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
  * @author Alefas
  * @since  21/10/15
  */
class ScalaProximityStatistician extends ProximityStatistician {
  override def serialize(element: PsiElement, location: ProximityLocation): StatisticsInfo = {
    val memberKey: String = element match {
      case f: ScFunction => s"function#${f.name}" + f.parameters.map(p => "#" + p.getType(TypingContext.empty).getOrAny.presentableText).mkString
      case o: ScObject => s"object#${o.qualifiedName}"
      case c: ScClass => s"class#${c.qualifiedName}"
      case t: ScTrait => s"trait#${t.qualifiedName}"
      case t: ScTypeAlias => s"typeAlias#${t.name}"
      case v: ScBindingPattern =>
        v.nameContext match {
          case _: ScValue => s"value#${v.name}"
          case _: ScVariable => s"variable${v.name}"
          case _ => return null
        }
      case f: ScFieldId =>
        f.nameContext match {
          case _: ScValue => s"value#${f.name}"
          case _: ScVariable => s"variable#${f.name}"
          case _ => return null
        }
      case c: ScClassParameter => s"classParameter#${c.name}"
      case p: ScParameter => s"parameter#${p.name}"
      case _ => return null
    }
    new StatisticsInfo("scalaMember#", memberKey)
  }
}