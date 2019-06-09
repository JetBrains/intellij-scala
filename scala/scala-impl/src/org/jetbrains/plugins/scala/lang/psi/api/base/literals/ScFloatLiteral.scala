package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

import java.lang.{Float => JFloat}

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

trait ScFloatLiteral extends ScLiteral {

  override def getValue: JFloat
}

object ScFloatLiteral {

  def unapply(literal: ScFloatLiteral): Option[Float] =
    Option(literal.getValue)

  final case class Value(override val value: JFloat) extends ScLiteral.Value(value) with ScLiteral.NumericValue {

    override def negate = Value(-value)

    override def presentation: String = super.presentation + 'f'

    override def wideType(implicit project: Project): ScType = api.Float
  }
}