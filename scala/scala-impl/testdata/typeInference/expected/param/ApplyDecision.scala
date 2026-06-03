object Test {
  object A {

    def apply(x: Int => Int) = 1

    def apply(x: Int, y: Int) = 2
  }
  val a = A
  a(p => /*start*/p/*end*/)
}
//Int