package org.jetbrains.plugins.scala.dfa

import org.jetbrains.plugins.scala.dfa.lattice.{JoinSemiLattice, Lattice}
import org.jetbrains.plugins.scala.dfa.lattice.specific.{FlatJoinSemiLattice, FlatLattice}

/**
 * A complete bool lattice
 *
 *       Top
 *     /    \
 *    /      \
 *  True    False
 *    \      /
 *     \    /
 *     Bottom
 */
sealed abstract class BoolLat(final val canBeTrue: Boolean, final val canBeFalse: Boolean)
  extends Product with Serializable
{
  final val isConcrete: Boolean = canBeTrue != canBeFalse

  final def asConcrete: Option[Boolean] =
    if (isConcrete) Some(canBeTrue) else None
}

object BoolLat {
  def apply(boolean: Boolean): BoolSemiLat =
    if (boolean) True else False

  def apply(boolean: Option[Boolean]): BoolLat =
    boolean.fold(Bottom: BoolLat)(BoolLat(_))

  sealed trait Concrete extends BoolSemiLat

  def unapply(boolean: Concrete): Some[Boolean] = Some(boolean.canBeTrue)

  /**
   * Describes a value that can be true as well as false
   */
  final case object Top extends BoolSemiLat(
    canBeTrue = true,
    canBeFalse = true,
  )

  final case object True extends BoolSemiLat(
    canBeTrue = true,
    canBeFalse = false,
  ) with Concrete

  final case object False extends BoolSemiLat(
    canBeTrue = false,
    canBeFalse = true,
  ) with Concrete

  /**
   * Describes a value that can neither be true nor false
   */
  final case object Bottom extends BoolLat(
    canBeTrue = false,
    canBeFalse = false,
  )

  implicit val lattice: Lattice[BoolLat] = new FlatLattice[BoolLat](Top, Bottom)
}

/**
 * A join-semi-lattice for boolean that does not have a Bottom element
 *
 *       Top
 *     /    \
 *    /      \
 *  True    False
 */
sealed abstract class BoolSemiLat(canBeTrue: Boolean, canBeFalse: Boolean) extends BoolLat(canBeTrue, canBeFalse)

object BoolSemiLat {
  val Top: BoolLat.Top.type = BoolLat.Top
  val True: BoolLat.True.type = BoolLat.True
  val False: BoolLat.False.type = BoolLat.False

  implicit val joinSemiLattice: JoinSemiLattice[BoolSemiLat] = new FlatJoinSemiLattice[BoolSemiLat](Top)
}