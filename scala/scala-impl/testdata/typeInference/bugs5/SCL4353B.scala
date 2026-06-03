object SCL4353B {
  def goo[T](x: Int => T): T = x(1)
  implicit def f(x: Int): String = ""
  def foo(x: Int): Int = x + 1
  val x: String = goo(/*start*/foo _/*end*/)
}
//Int => String