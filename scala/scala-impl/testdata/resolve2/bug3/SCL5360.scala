object Test {
  class C extends B {
    trait Sym
    class X {
      var cu: CU = null // okay if this is a `val`.
    }
  }
  trait B { self: C =>
    class Namer1(val x: X) {
      val m: Sym = null
      x.cu.obj./* line: 15 */foo(m) // Expected C.this.type#Sym, actual B.this.type#Sym
    }
    class CU {
      object obj {
        def foo(sym: Sym) = null
        def foo(i: Int) = null
      }
    }
  }
}