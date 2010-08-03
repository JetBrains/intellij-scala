object A {
  trait B {
    private[A] def foo: Int = 1
  }

  class C extends B {
    /* line: 3 */foo
  }
}

class G extends A.B {
  /* resolved: false */foo
}