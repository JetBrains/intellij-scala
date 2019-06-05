package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

trait ScNullLiteral extends ScLiteral {

  override def getValue: Null
}

object ScNullLiteral {

  private[this] val TypeKey = Key.create[ScType]("scala.type.without.implicits")

  def unapply(literal: ScNullLiteral): Option[ScType] =
    Option(literal.getCopyableUserData(TypeKey))

  def update(literal: ScNullLiteral,
             `type`: ScType): Unit = {
    literal.putCopyableUserData(TypeKey, `type`)
  }

  final case object Value extends ScLiteral.Value(null) {

    override def wideType(implicit project: Project): ScType = api.Null
  }
}
