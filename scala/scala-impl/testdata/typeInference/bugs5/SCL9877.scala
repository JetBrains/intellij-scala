class SCL9877 {
  def foo(implicit c: Int): String = "foo"
  implicit val c = 42
  implicit def f = foo
  implicit val a = foo
  implicit def g = foo
  /*start*/(a, f, g)/*end*/
}
//(String, String, String)