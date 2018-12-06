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

  def isClassMember: Boolean = isVal || isVar || isCaseClassVal

  /** Is the parameter automatically a val, due to it's position in a case class parameter list */
  def isCaseClassVal: Boolean

  override def accept[T](visitor: JvmElementVisitor[T]): T = super[ScMember].accept(visitor)
}