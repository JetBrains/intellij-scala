object SCL6169 {
  class A {
    class AWord {
      def apply(s: String) = 123

      def should(s : String) = 123
    }
    val a = new AWord
    def a[T: Manifest] = new AWord
  }

  object B extends A {
    /*start*/a[ClassNotFoundException] should "not be red"/*end*/
  }
}
//Int