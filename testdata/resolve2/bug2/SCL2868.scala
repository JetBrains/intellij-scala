object test {
  class A; object A extends A

  trait AA {
    def a_=(x: A) {};
  }

  object Imp extends AA {
    def a: Any = 0
  }

  {
    import Imp.a
    /* resolved: true */a = A
  }

  {
    import Imp.a_=
    /*resolved: false */a = A
  }

  {
    import Imp.a_=
    val a: Any = 0
    /*resolved: true */a = A
  }
}