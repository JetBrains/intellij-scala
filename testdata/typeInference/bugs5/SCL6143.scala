object SCL6143 extends App {
  class A {
    class B {
      def foo: A.this.type = A.this
    }
  }

  class B extends A {
    class B extends super[A].B {
      val x = super.foo
    }
    /*start*/(new B().x, new B().foo)/*end*/
  }
}
//(SCL6143.B, SCL6143.B)