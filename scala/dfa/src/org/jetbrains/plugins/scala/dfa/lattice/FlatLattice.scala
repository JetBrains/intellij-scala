package org.jetbrains.plugins.scala.dfa
package lattice

/**
 * Lattice type class implementation for flat lattices
 *
 * A flat lattice has a Top, a Bottom, and possibly infinitely many unrelated other elements in between.
 */
class FlatLattice[T](override val top: T, override val bottom: T)
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

  override def meet(lhs: T, rhs: T): T = (lhs, rhs) match {
    case (a, b) if a == b => a
    case (`top`, a) => a
    case (a, `top`) => a
    case _ => bottom
  }
}