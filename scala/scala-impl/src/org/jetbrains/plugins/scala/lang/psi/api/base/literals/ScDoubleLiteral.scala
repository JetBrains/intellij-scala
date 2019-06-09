package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

import java.lang.{Double => JDouble}

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

trait ScDoubleLiteral extends ScLiteral {

  override def getValue: JDouble
}

object ScDoubleLiteral {

  def unapply(literal: ScDoubleLiteral): Option[Double] =
    Option(literal.getValue)

  final case class Value(override val value: JDouble) extends ScLiteral.Value(value) with ScLiteral.NumericValue {

    override def negate = Value(-value)

    override def wideType(implicit project: Project): ScType = api.Double
  }
}