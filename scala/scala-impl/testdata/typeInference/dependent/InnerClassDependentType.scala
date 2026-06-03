object InnerClassDependentType {

  class A[T](x: T) {
    def inner: this.Inner = new Inner

    class Inner {
      def map: T = x
    }

  }

  object B {
    var a = new A(1)
    val a2 = (new A(1)).inner
    /*start*/a2.map/*end*/
  }

}
//Int