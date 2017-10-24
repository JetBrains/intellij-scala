class A {
  type A <: S
  class S
  val a = 1
}

class B extends A {
  class A extends S
  override val a = 2
}

val b = new B

class C {
  def foo(a: A#A) = 1
  def foo(s: String) = "text"
}

/*start*/new C().foo(new b.A)/*end*/
//Int