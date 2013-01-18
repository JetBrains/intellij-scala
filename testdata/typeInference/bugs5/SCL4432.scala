object Test {
  def main(args: Array[String]) {
    class Test2 {
      val b: Test2 = this
      def foo(t: Test2) = 1
      def foo(s: String) = false

      /*start*/foo(this)/*end*/
    }
    val x = new Test2
    println(x.b == x)
  }
}
//Int