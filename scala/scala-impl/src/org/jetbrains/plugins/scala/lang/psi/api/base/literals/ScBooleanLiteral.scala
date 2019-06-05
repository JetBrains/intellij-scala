package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

import java.lang.{Boolean => JBoolean}

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

trait ScBooleanLiteral extends ScLiteral {

  override def getValue: JBoolean
}

object ScBooleanLiteral {

  def unapply(literal: ScBooleanLiteral): Some[JBoolean] =
    Some(literal.getValue)

  final case class Value(override val value: JBoolean) extends ScLiteral.Value(value) {

    override def wideType(implicit project: Project): ScType = api.Boolean
  }
}