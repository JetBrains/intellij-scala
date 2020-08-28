package org.jetbrains.plugins.scala.dfa.lattice
package specific

import scala.annotation.tailrec

/**
 * Lattice type class implementation for flat lattices
 *
 * A flat lattice has a Top, a Bottom, and possibly infinitely many unrelated other elements in between.
 */
final class FlatLattice[T](override val top: T, override val bottom: T)
  extends JoinSemiLattice[T] with MeetSemiLattice[T]
{
  override def <=(subSet: T, superSet: T): Boolean = (subSet, superSet) match {
    case (a, b) if a == b => true
    case (_, `top`) => true
    case (`bottom`, _) => true
    case _ => false
  }

  override def intersects(lhs: T, rhs: T): Boolean = (lhs, rhs) match {
    case (_, `bottom`) => false
    case (`bottom`, _) => false
    case (a, b) if a == b => true
    case (`top`, _) => true
    case (_, `top`) => true
    case _ => false
  }

  override def join(lhs: T, rhs: T): T = (lhs, rhs) match {
    case (a, b) if a == b => a
    case (`bottom`, a) => a
    case (a, `bottom`) => a
    case _ => top
  }

  @tailrec
  override def joinAll(first: T, others: IterableOnce[T]): T = {
    first match {
      case `top` => top
      case `bottom` =>
        val it = others.iterator
        if (it.hasNext) joinAll(it.next(), it)
        else bottom
      case concrete =>
        val allTheSame = others.iterator.forall(b => b == concrete || b == bottom)
        if (allTheSame) concrete
        else top
    }
  }

  override def meet(lhs: T, rhs: T): T = (lhs, rhs) match {
    case (a, b) if a == b => a
    case (`top`, a) => a
    case (a, `top`) => a
    case _ => bottom
  }

  @tailrec
  override def meetAll(first: T, others: IterableOnce[T]): T = {
    first match {
      case `bottom` => bottom
      case `top` =>
        val it = others.iterator
        if (it.hasNext) meetAll(it.next(), it)
        else top
      case concrete =>
        val allTheSame = others.iterator.forall(b => b == concrete || b == top)
        if (allTheSame) concrete
        else bottom
    }
  }
}