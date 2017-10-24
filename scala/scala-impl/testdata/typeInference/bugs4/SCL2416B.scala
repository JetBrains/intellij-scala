object test {
  trait A
  trait B extends A

  def sa2: Set[A] = /*start*/for{
    x <- Set(1)
  } yield new B {}/*end*/
}
//Set[test.A]