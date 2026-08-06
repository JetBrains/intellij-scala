object SCL9306B extends App {
  class A
  class B
  class C
  class D
  object Implicits {
    implicit def convert(f: A => B): (C => D) = { c: C => new D }
  }

  import Implicits._

  val func3: (A => B) = { a: A => new B }

  /*start*/func3(new C)/*end*/
}
//SCL9306B.D