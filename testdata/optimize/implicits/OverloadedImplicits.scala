package overloaded

object OverloadedImplicits {
  class A
  class B
  object Implicits {
    implicit def a2b(x: A): B = new B
  }

  object Test {
    import Implicits.a2b

    def a(x: B) = 1
    def a(x: Int) = 2

    a(new A)
  }
}
/*package overloaded

object OverloadedImplicits {
  class A
  class B
  object Implicits {
    implicit def a2b(x: A): B = new B
  }

  object Test {
    import Implicits.a2b

    def a(x: B) = 1
    def a(x: Int) = 2

    a(new A)
  }
}*/