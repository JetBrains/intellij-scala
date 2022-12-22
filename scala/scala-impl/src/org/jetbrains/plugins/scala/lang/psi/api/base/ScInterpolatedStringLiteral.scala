package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScReferenceExpression}

trait ScInterpolatedStringLiteral extends ScStringLiteral with ScInterpolated {

  def kind: ScInterpolatedStringLiteral.Kind

  def reference: Option[ScReferenceExpression]

  def referenceName: String

  def desugaredExpression: Option[(ScReferenceExpression, ScMethodCall)]

  override final def isSimpleLiteral: Boolean = false
}

object ScInterpolatedStringLiteral {

  def unapply(literal: ScInterpolatedStringLiteral): Option[ScReferenceExpression] =
    literal.reference

  sealed abstract class Kind(val prefix: String)

  object Kind {
    val stdLibKinds: Set[Kind] = Set(Standard, Format, Raw)

    def fromPrefix(prefix: String): Kind =
      stdLibKinds.find(_.prefix == prefix).getOrElse(Pattern(prefix))
  }

  case object Standard extends Kind("s")

  case object Format extends Kind("f")

  case object Raw extends Kind("raw")

  case class Pattern(override val prefix: String) extends Kind(prefix)

}
