package org.jetbrains.plugins.scala.dfa

import org.jetbrains.plugins.scala.dfa.BoolLat.SemiLatticeImpl

import scala.annotation.tailrec

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
sealed abstract class BoolLat(final val canBeTrue: Boolean, final val canBeFalse: Boolean) {
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

  class SemiLatticeImpl[Lat <: BoolLat] extends SemiLattice[Lat] {
    override def <=(subSet: Lat, superSet: Lat): Boolean =
      subSet.canBeFalse <= superSet.canBeFalse && subSet.canBeTrue <= superSet.canBeTrue

    override def intersects(lhs: Lat, rhs: Lat): Boolean =
      lhs.canBeFalse && rhs.canBeFalse || lhs.canBeTrue && rhs.canBeTrue
  }

  implicit val lattice: Lattice[BoolLat] =
    new SemiLatticeImpl[BoolLat] with JoinSemiLattice[BoolLat] with MeetSemiLattice[BoolLat] {
      override def top: BoolLat = Top
      override def bottom: BoolLat = Bottom

      override def join(lhs: BoolLat, rhs: BoolLat): BoolLat =
        (lhs, rhs) match {
          case (Bottom, a) => a
          case (a, Bottom) => a
          case (a, b) if a == b => a
          case _ => Top
        }

      @tailrec
      override def joinAll(first: BoolLat, others: TraversableOnce[BoolLat]): BoolLat = {
        first match {
          case Top => Top
          case Bottom =>
            val it = others.toIterator
            if (it.hasNext) joinAll(it.next(), it)
            else Bottom
          case concrete =>
            val allTheSame = others.forall(b => b == concrete || b == Bottom)
            if (allTheSame) concrete
            else Top
        }
      }

      override def meet(lhs: BoolLat, rhs: BoolLat): BoolLat =
        (lhs, rhs) match {
          case (Top, a) => a
          case (a, Top) => a
          case (a, b) if a == b => a
          case _ => Bottom
        }

      @tailrec
      override def meetAll(first: BoolLat, others: TraversableOnce[BoolLat]): BoolLat = {
        first match {
          case Bottom => Bottom
          case Top =>
            val it = others.toIterator
            if (it.hasNext) meetAll(it.next(), it)
            else Top
          case concrete =>
            val allTheSame = others.forall(b => b == concrete || b == Top)
            if (allTheSame) concrete
            else Bottom
        }
      }
    }
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

  implicit val semiLattice: SemiLattice[BoolSemiLat] = new SemiLatticeImpl

  implicit val joinSemiLattice: JoinSemiLattice[BoolSemiLat] =
    new SemiLatticeImpl[BoolSemiLat] with JoinSemiLattice[BoolSemiLat] {
      override def top: BoolSemiLat = Top

      override def join(lhs: BoolSemiLat, rhs: BoolSemiLat): BoolSemiLat =
        if (lhs == rhs) lhs else Top

      override def joinAll(first: BoolSemiLat, others: TraversableOnce[BoolSemiLat]): BoolSemiLat = {
        first match {
          case Top => Top
          case concrete =>
            val allTheSame = others.forall(b => b == concrete)
            if (allTheSame) concrete
            else Top
        }
      }
    }
}