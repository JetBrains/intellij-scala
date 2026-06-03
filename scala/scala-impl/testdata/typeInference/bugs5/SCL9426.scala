object SCL9426 {
  trait TC[A]

  object E {
    implicit def e[X: TC, Y: TC]: TC[Either[X, Y]] = ???
  }

  object Demo {
    import E._

    trait A
    trait B

    implicit val a: TC[A] = ???
    implicit val b: TC[B] = ???
    //  implicit val ab: TC[Either[A, B]] = e

    class F1[X](implicit PV: TC[X]) {
      type Instance = Unit
    }

    object F2 extends F1[Either[A, B]]

    val asd : F2.Instance = ???

    /*start*/asd/*end*/
  }
}
//Demo.F2.Instance