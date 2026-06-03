trait A {
  type PInt1 = (Int, Any)
  type PInt2 <: (Int, Any)
  type PInt3 = PInt1
  type PA1[A] = (A, A)
  type PA2[A] = Tuple2[A, Any]
  type PA3[A] = PA1[A]
  def x[T]: T

  val i1 = x[PInt1] match {
    case (a, b) => a
  }
  val i2 = x[PInt2] match {
    case (a, b) => a
  }
  val i3 = x[PInt3] match {
    case (a, b) => a
  }
  val i4 = x[PA1[Int]] match {
    case (a, b) => a
  }
  val i5 = x[PA2[Int]] match {
    case (a, b) => a
  }
  val i6 = x[PA3[Int]] match {
    case (a, b) => a
  }
}
object B extends A {
  val i7 = x[PInt2] match {
    case (a, b) => a
  }
}

import B._

/*start*/(i1, i3, i4, i5, i6)/*end*/
// (Int, Int, Int, Int, Int)