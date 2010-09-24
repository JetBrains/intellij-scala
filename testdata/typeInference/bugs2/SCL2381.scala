class S1 {def s = this}
class S2 {def s = this}

object simple {
  implicit def m1(f: Int => Int) = new S1

  implicit def m2(l: Seq[Int]) = new S2

  val c = List(1).s // correctly resolves m2
}

object param1 {
  implicit def m1[A](f: Int => A) = new S1

  implicit def m2[A](l: Seq[A]) = new S2

  val c = List(1).s // correctly resolves m2
}

object param2 {
  implicit def m1[I, A](f: I => A) = new S1

  implicit def m2[A](l: Seq[A]) = new S2

  val c = List(1).s // incorrectly resolves as ambiguous
}
object param3 {
  implicit def m1[I, A](f: I => A) = new S1

  implicit def m2[M[X] <: Seq[X], A](l: M[A]) = new S2

  val c = List(1).s // incorrectly resolves m1
}
/*start*/(simple.c, param1.c, param2.c, param3.c)/*end*/

//(S1, S2, S2, S2)