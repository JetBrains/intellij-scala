package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait ScSymbolLiteral extends ScLiteral {

  override def getValue: Symbol
}

object ScSymbolLiteral {

  def unapply(literal: ScSymbolLiteral): Option[Symbol] =
    Option(literal.getValue)

  final case class Value(override val value: Symbol) extends ScLiteral.Value(value) {

    override def wideType(implicit project: Project): ScType = ScLiteral.cachedClass("scala.Symbol")
  }
}
