object SCL2806B {
  class A
  class C extends A
  class B[T <: A](x: T) {
    def foo(): T = x
  }
  val z: B[_ <: A] = null

  (z, new B(new C)) match {
    case (x: B[_], y: B[_]) =>
      /*start*/(x, y)/*end*/
  }
}
//(SCL2806B.B[_], SCL2806B.B[SCL2806B.C])