package org.jetbrains.plugins.scala.lang.psi.api.base
package literals

import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait ScNullLiteral extends ScLiteral {
  override protected type V = Null

  override final def isSimpleLiteral: Boolean = false
}

object ScNullLiteral {

  private[this] val TypeKey = Key.create[ScType]("scala.type.without.implicits")

  def unapply(literal: ScNullLiteral): Option[ScType] =
    Option(literal.getCopyableUserData(TypeKey))

  def update(literal: ScNullLiteral,
             `type`: ScType): Unit = {
    literal.putCopyableUserData(TypeKey, `type`)
  }
}
