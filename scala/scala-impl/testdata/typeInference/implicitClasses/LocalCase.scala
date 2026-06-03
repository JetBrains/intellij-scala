object LocalCase {
  def foo() {
    implicit class A(x: Int) {
      def goo: Int = 1
    }
    /*start*/1.goo/*end*/
  }
}
//Int