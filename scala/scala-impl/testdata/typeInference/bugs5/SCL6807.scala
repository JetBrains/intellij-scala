object SCL6807 {
  object literal extends scala.Dynamic {
    def applyDynamic(s: String)(r: String) = 1
    def applyDynamicNamed(s : String)(r: (String, Any)) = "2"
  }

  val x = literal

  /*start*/(literal.foo(x = 2),
    literal(""),
    this literal (x = 2),
    literal(x = 2),
    x(""),
    this x (x = 2),
    x(x = 2))/*end*/
}
//(String, Int, String, String, Int, String, String)