package org.jetbrains.plugins.scala.lang.psi.api.statements.params

import com.intellij.lang.jvm.JvmElementVisitor
import javax.swing.Icon
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScDecoratedIconOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember}

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

trait ScClassParameter extends ScParameter with ScMember with ScDecoratedIconOwner {
  def isVal: Boolean

  def isVar: Boolean

  def isPrivateThis: Boolean

  // TODO isEffectiveValOrVar? (or isClassMember?)
  /** Is the parmameter is explicitly marked as a val or a var; or a case class parameter that is automatically a val. */
  def isEffectiveVal: Boolean = isVal || isVar || isCaseClassVal

  /** Is the parameter automatically a val, due to it's position in a case class parameter list */
  def isCaseClassVal: Boolean = containingClass match {
    case c: ScClass if c.isCase =>
      val isInPrimaryConstructorFirstParamSection = c.constructor match {
        case Some(const) => const.effectiveFirstParameterSection.contains(this)
        case None => false
      }
      // Any modifier (including "override", "final" or "private") requires "val" or "var"
      val isValOrVar = Option(getModifierList).exists(_.hasExplicitModifiers)
      isInPrimaryConstructorFirstParamSection && !isValOrVar
    case _ => false
  }

  override protected def getBaseIcon(flags: Int): Icon =
    if (isVar) Icons.FIELD_VAR
    else if (isVal || isCaseClassVal) Icons.FIELD_VAL
    else Icons.PARAMETER

  override def accept[T](visitor: JvmElementVisitor[T]): T = super[ScMember].accept(visitor)
}