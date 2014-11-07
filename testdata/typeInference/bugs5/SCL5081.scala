object SCL5081 {
abstract case class Foo(x: Int)

object Foo {
  def apply(a: Int)(s: String) = "text"
}
/*start*/Foo(0)("t")/*end*/
}
//String