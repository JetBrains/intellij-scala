package org.jetbrains.plugins.scala.lang.psi.types.api

/**
  * Nikolay.Tropin
  * 09-Aug-17
  */
sealed abstract class Variance(val name: String) {
  def unary_- : Variance

  def inverse(b: Boolean): Variance = if (b) -this else this

  def *(other: Variance): Variance = other match {
    case Invariant => Invariant
    case Covariant => this
    case Contravariant => -this
  }
}

case object Invariant extends Variance("invariant") {
  override def unary_- : Variance = this
}

case object Covariant extends Variance("covariant") {
  override def unary_- : Variance = Contravariant
}

case object Contravariant extends Variance("contravariant") {
  override def unary_- : Variance = Covariant
}