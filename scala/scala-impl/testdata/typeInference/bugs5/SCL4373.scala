object SCL4373 {
  class C[A]
  object Test {
    def foo[A, B <: C[A]](arg: B with C[A]) = arg
    val x: C[Int] = new C[Int]
    val y: Set[C[Int]] = /*start*/Set(foo(x))/*end*/ //error highlighting is not stable here:(
  }
}
//Set[SCL4373.C[Int]]