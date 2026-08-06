object SCL4353C {
  def foo(x: Int)(y: Int): Int = x + y
  implicit def goo(x: Int): String = "text"
  val x: Int => Int => String = /*start*/foo _/*end*/
}
//Int => Int => String