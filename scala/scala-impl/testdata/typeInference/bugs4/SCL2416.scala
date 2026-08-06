object test {
  trait A
  trait B extends A

  val sa1: Set[A] = /*start*/for{
    x <- Set(1)
  } yield new B {}/*end*/
}
//Set[test.A]