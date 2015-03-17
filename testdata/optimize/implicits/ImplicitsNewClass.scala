package overloaded
object ImplicitsNewClass {
  class A
  class B
  class C(x: B) {
    def this(x: Int) = this(new B)
  }
  object Implicits {
    implicit def a2b(x: A): B = new B
  }
  object Test {
    import Implicits.a2b
    new C(new A)
  }
}
/*package overloaded
object ImplicitsNewClass {
  class A
  class B
  class C(x: B) {
    def this(x: Int) = this(new B)
  }
  object Implicits {
    implicit def a2b(x: A): B = new B
  }
  object Test {
    import Implicits.a2b
    new C(new A)
  }
}*/