object ClassParameterResolve {
  class A(val o: Int)

  class B(o: Int) extends A(/* line: 4 */o + 1) {
    val i = /* line: 4 */o
  }

  object Main {
    val a = new A(2)
    val b = new B(3)

    def main(args: Array[String]) {
      println(a./* line: 2 */o)
      println(b./* line: 2 */o)
    }
  }
}