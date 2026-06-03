class S1 {def s = this}
class S2 {def s = this}

object param1 {
  trait T[-A]
  trait U extends T[Int]

  implicit def m1[M11](f: T[M11]) = new S1
  implicit def m2(l: U) = new S2

  val u: U = new U {}
  val c = u.s // incorrectly resolves as ambiguous
}

/*start*/param1.c/*end*/

//S2