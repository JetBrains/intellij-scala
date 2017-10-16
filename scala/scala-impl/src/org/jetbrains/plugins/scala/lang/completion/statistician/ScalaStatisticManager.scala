package org.jetbrains.plugins.scala.lang.completion.statistician

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable.TypingContext


object ScalaStatisticManager {
  def memberKey(element: PsiElement): Option[String] = {
    val value = element match {
      case f: ScFunction => s"function#${f.name}" +
        f.parameters.map(p => "#" + p.getType(TypingContext).getOrAny.presentableText).mkString
      case o: ScObject => s"object#${o.qualifiedName}"
      case c: ScClass => s"class#${c.qualifiedName}"
      case t: ScTrait => s"trait#${t.qualifiedName}"
      case t: ScTypeAlias => s"typeAlias#${t.name}"
      case v: ScBindingPattern =>
        v.nameContext match {
          case _: ScValue => s"value#${v.name}"
          case _: ScVariable => s"variable${v.name}"
          case _ => return None
        }
      case f: ScFieldId =>
        f.nameContext match {
          case _: ScValue => s"value#${f.name}"
          case _: ScVariable => s"variable#${f.name}"
          case _ => return None
        }
      case c: ScClassParameter => s"classParameter#${c.name}"
      case p: ScParameter => s"parameter#${p.name}"
      case _ => return None
    }
    Some(value)
  }
}