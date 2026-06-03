object SCL5222 {
  class Temp {
    protected[this] def foo() = "Hello"

    def foo(s: String): Int = 123
  }

  class Temp2 extends Temp {
    override def foo() = {
      val str = super.foo()
      /*start*/str/*end*/
      ""
    }
  }
}
//String