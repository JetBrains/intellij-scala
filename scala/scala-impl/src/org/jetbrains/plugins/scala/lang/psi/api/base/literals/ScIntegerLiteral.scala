package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

trait ScIntegerLiteral extends ScLiteral {

  override def getValue: Integer
}

object ScIntegerLiteral {

  def unapply(literal: ScIntegerLiteral): Option[Int] =
    Option(literal.getValue)

  final case class Value(override val value: Integer) extends ScLiteral.Value(value) with ScLiteral.NumericValue {

    override def negate = Value(-value)

    override def wideType(implicit project: Project): ScType = api.Int
  }
}
