package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

import java.lang.{Long => JLong}

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

trait ScLongLiteral extends ScLiteral {

  override def getValue: JLong
}

object ScLongLiteral {

  def unapply(literal: ScLongLiteral): Option[Long] =
    Option(literal.getValue)

  final case class Value(override val value: JLong) extends ScLiteral.Value(value) with ScLiteral.NumericValue {

    override def negate = Value(-value)

    override def presentation: String = super.presentation + 'L'

    override def wideType(implicit project: Project): ScType = api.Long
  }
}
