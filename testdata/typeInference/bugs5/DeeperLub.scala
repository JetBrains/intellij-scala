object DeeperLub {
  case class A[+T1, +T2](t1: T1, t2: T2)
  val f = 1 match {
    case 1 =>
      A(1, A(1, A(1, A(1, A(1, Some(1))))))
    case _ =>
      A(2, A(2, A(2, A(2, A(2, None)))))
  }
  
  class Az[+T] {}
  class B extends Az[B]
  class C extends Az[C]

  val g = 1 match {
    case 1 => new B
    case 2 => new C
  }

  /*start*/(f, g)/*end*/
}
//(DeeperLub.A[Int, DeeperLub.A[Int, DeeperLub.A[Int, DeeperLub.A[Int, DeeperLub.A[Int, Option[Int]]]]]], DeeperLub.Az[DeeperLub.Az[Any]])