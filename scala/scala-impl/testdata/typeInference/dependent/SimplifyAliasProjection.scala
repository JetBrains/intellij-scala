object Test {
  trait DepFn1[T] {
    type Out
    def apply(t: T): Out
  }

  trait Adjoin[A] extends DepFn1[A]

  trait LowPriorityAdjoin {
    type Aux[A, Out0] = Adjoin[A] { type Out = Out0 }
  }

  object Adjoin extends LowPriorityAdjoin

  val auxOut: Adjoin.Aux[Int, Boolean]#Out = ???

  /*start*/auxOut/*end*/
}
//Boolean