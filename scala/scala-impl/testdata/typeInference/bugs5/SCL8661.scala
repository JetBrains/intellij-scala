object SCL8661 {

  trait Generic[T] {
    def f1(s: String): T
  }

  trait A extends Generic[String] with B {

    override def f1(s: String) = {
      /*start*/f2(s)/*end*/
    }
  }

  trait B {
    _: A =>

    def f2(s: String) = f1(s)
  }

}
//String