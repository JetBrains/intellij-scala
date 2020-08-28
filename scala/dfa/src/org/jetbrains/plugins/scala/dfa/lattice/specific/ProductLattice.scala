package org.jetbrains.plugins.scala.dfa.lattice
package specific

import scala.reflect.ClassTag


abstract class ProductLattice[T <: AnyRef: ClassTag](_top: => T, override val bottom: T, elementLattices: Array[Lattice[_ <: T]])
  extends JoinSemiLattice[T] with MeetSemiLattice[T] {

  override lazy val top: T = _top

  override final def <=(subSet: T, superSet: T): Boolean = {
    compare[T](subSet, superSet, intersect = false)((a, b, lattice) => lattice.<=(a, b))
  }

  override final def intersects(lhs: T, rhs: T): Boolean =
    compare[T](lhs, rhs, intersect = true)((a, b, lattice) => lattice.intersects(a, b))

  override final def join(lhs: T, rhs: T): T =
    combine[T](lhs, rhs, join = true)((a, b, lattice) => lattice.join(a, b))

  override final def meet(lhs: T, rhs: T): T =
    combine[T](lhs, rhs, join = false)((a, b, lattice) => lattice.meet(a, b))


  protected def createTuple(elements: Array[T]): T

  protected trait ProductTupleBase  {
    def elements: Array[T]

    // this is needed so the unapply check in `ProductTuple` works only for instances of only this lattice
    private[ProductLattice] def _lattice: ProductLattice[T] = ProductLattice.this
  }

  private final object ProductTuple {
    def unapply(productTuple: ProductTupleBase): Some[Array[T]] = Some(productTuple.elements)
  }

  private val bottomTuple: Array[T] = elementLattices.map(_.bottom)


  private def compare[R](a: T, b: T, intersect: Boolean)
                        (f: (AnyRef, AnyRef, Lattice[AnyRef]) => Boolean): Boolean =
    (a, b) match {
      case (ProductTuple(a), ProductTuple(b)) =>
        val length = elementLattices.length
        var i = 0
        while (i < length) {
          val res = f(a(i), b(i), elementLattices(i).asInstanceOf[Lattice[AnyRef]])
          if (intersect) {
            if (res)
              return true
          } else {
            if (!res)
              return false
          }

          i += 1
        }

        !intersect
      case (ProductTuple(a), b) =>
        intersect && indexOf(b).exists(i => f(a(i), b, elementLattices(i).asInstanceOf[Lattice[AnyRef]]))

      case (a, ProductTuple(b)) =>
        indexOf(a).fold(!intersect)(i => f(a, b(i), elementLattices(i).asInstanceOf[Lattice[AnyRef]]))

      case (a, b) =>
        (indexOf(a), indexOf(b)) match {
          case (None, _) => !intersect
          case (Some(ai), Some(bi)) =>
            if (ai == bi) f(a, b, elementLattices(ai).asInstanceOf[Lattice[AnyRef]])
            else false
          case _ => false
        }
    }

  private def combine[R](a: T, b: T, join: Boolean)
                        (f: (AnyRef, AnyRef, Lattice[AnyRef]) => AnyRef): T =
    (a, b) match {
      case (ProductTuple(a), ProductTuple(b)) =>
        val length = elementLattices.length
        val result = new Array[T](length)

        var i = 0
        while (i < length) {
          result(i) = f(a(i), b(i), elementLattices(i).asInstanceOf[Lattice[AnyRef]]).asInstanceOf[T]
          i += 1
        }

        createTuple(result)
      case (ProductTuple(aTup), b) =>
        indexOf(b).fold(if (join) a else bottom) { i =>
          val combined = f(aTup(i), b, elementLattices(i).asInstanceOf[Lattice[AnyRef]]).asInstanceOf[T]
          if (join) {
            val clone = aTup.clone()
            clone(i) = combined
            createTuple(clone)
          } else combined
        }

      case (a, ProductTuple(bTup)) =>
        indexOf(a).fold(if (join) b else bottom) { i =>
          val combined = f(bTup(i), a, elementLattices(i).asInstanceOf[Lattice[AnyRef]]).asInstanceOf[T]
          if (join) {
            val clone = bTup.clone()
            clone(i) = combined
            createTuple(clone)
          } else combined
        }

      case (a, b) =>
        (indexOf(a), indexOf(b)) match {
          case (_, None) => if (join) a else bottom
          case (None, _) => if (join) b else bottom
          case (Some(ai), Some(bi)) =>
            if (ai == bi) {
              f(a, b, elementLattices(ai).asInstanceOf[Lattice[AnyRef]]).asInstanceOf[T]
            } else if (join) {
              val result = bottomTuple.clone()
              result(ai) = a
              result(bi) = b
              createTuple(result)
            } else bottom
        }
    }

  protected def indexOf(element: T): Option[Int]
}
