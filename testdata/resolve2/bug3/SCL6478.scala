class SCL6478 {
  trait A
  trait B
  class C extends A with B

  class G {
    def foo = 1
  }

  val c = new C

  implicit def a2g(a: A): G = new G
  implicit def b2g(b: B): G = new G

  implicit def a2b(a: A): B = new B {}

  c./* resolved: false */foo
}