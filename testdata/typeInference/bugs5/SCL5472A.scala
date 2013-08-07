class SCL5472A {
  class C

  class A

  class B extends A

  class D extends B

  def foo(z: C {type T <: B}) = 1
  def foo(z: Any) = "text"

  /*start*/(foo(new C {type T = A}), foo(new C {type T = D}))/*end*/
}
//(String, Int)