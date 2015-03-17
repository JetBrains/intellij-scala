package apply
object ImplicitApply {
  class A
  class B {
    def apply(x: Int) = 1
  }
  object Implicits {
    implicit def a2b(x: A): B = new B
  }
  object Test {
    import Implicits.a2b
    val a = new A
    a(1)
  }
}
/*package apply
object ImplicitApply {
  class A
  class B {
    def apply(x: Int) = 1
  }
  object Implicits {
    implicit def a2b(x: A): B = new B
  }
  object Test {
    import Implicits.a2b
    val a = new A
    a(1)
  }
}*/