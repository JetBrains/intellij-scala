class InImportSelector {
  class B {
    def foo2 = 2
  }
  class A extends B {
    def foo3 = 1
  }
  class C {
    def foo1 = 3
  }
  implicit def a2c(a: A): C = new C
  val a: A
  a.foo<caret>
}
