object Test {

  class A(x: Int) {
    def foo() = x
  }

  class Z extends (String => A) {
    override def apply(v1: String): A = new A(4)
  }
  implicit val s : Z = new Z

  /*start*/"".foo()/*end*/
}
//Int