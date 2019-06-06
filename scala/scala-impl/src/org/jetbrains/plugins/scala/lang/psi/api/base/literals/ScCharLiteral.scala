package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package literals

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

trait ScCharLiteral extends ScLiteral {

  override def getValue: Character
}

object ScCharLiteral {

  def unapply(literal: ScCharLiteral): Option[Character] =
    Option(literal.getValue)

  final case class Value(override val value: Char) extends ScLiteral.Value(value) {

    import ScLiteral.CharQuote

    override def presentation: String = CharQuote + super.presentation + CharQuote

    override def wideType(implicit project: Project): ScType = api.Char
  }
}
