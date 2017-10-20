class SCL9877_1 {
  def foo(implicit c: Int): String = "foo"
  implicit val c = 42
  implicit val a = foo
  implicit val b = foo
  /*start*/(a, b)/*end*/
}
//(String, String)