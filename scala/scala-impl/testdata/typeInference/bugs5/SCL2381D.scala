object MethodSpecificity {
  trait PartialApply1Of2[T[_, _], A] {
    type Apply[B] = T[A, B]

    type Flip[B] = T[B, A]
  }

  class MA[M[_], A](val s: String)

  object simple {
    implicit def m1(f: Int => Int): MA[PartialApply1Of2[Function1, Int]#Apply, Int] = new MA[PartialApply1Of2[Function1, Int]#Apply, Int]("Function1")
    implicit def m2(l: Seq[Int]): MA[Seq, Int] = new MA[Seq, Int]("Seq")
    val x = List(1).s // correctly resolves m2
  }

  object param1 {
    implicit def m1[A](f: Int => A): MA[PartialApply1Of2[Function1, Int]#Apply, A] = new MA[PartialApply1Of2[Function1, Int]#Apply, A]("Function1")
    implicit def m2[A](l: Seq[A]): MA[Seq, A] = new MA[Seq, A]("Seq")
    val x = List(1).s // correctly resolves m2
  }

  object param2 {
    implicit def m1[I, A](f: I => A): MA[PartialApply1Of2[Function1, I]#Apply, A] = new MA[PartialApply1Of2[Function1, I]#Apply, A]("Function1")
    implicit def m2[A](l: Seq[A]): MA[Seq, A] = new MA[Seq, A]("Seq")
    val x = List(1).s // incorrectly resolves as ambiguous
  }
  object param3 {
    implicit def m1[I, A](f: I => A): MA[PartialApply1Of2[Function1, I]#Apply, A] = new MA[PartialApply1Of2[Function1, I]#Apply, A]("Function1")
    implicit def m2[M[X] <: Seq[X], A](l: M[A]): MA[M, A] = new MA[M, A]("Seq")
    val x = List(1).s // incorrectly resolves as m1
  }
  /*start*/(simple.x,
  param1.x,
  param2.x,
  param3.x)/*end*/
}
//(String, String, String, String)