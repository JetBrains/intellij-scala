object AA {
  implicit class Def(val x: Int) extends AnyVal {
    def foo() = x + 1
  }

  /*start*/1.foo()/*end*/
}
//Int