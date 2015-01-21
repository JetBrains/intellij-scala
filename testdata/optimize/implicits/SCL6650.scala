class RR {
  class A {
    def foo = 123
  }
  class B

  object D {
    implicit val s: B = new B
  }

  object K {
    implicit def i2a(i: Int)(implicit b: B): A = new A
  }

  import D._
  import K._

  123.foo
}
/*
class RR {
  class A {
    def foo = 123
  }
  class B

  object D {
    implicit val s: B = new B
  }

  object K {
    implicit def i2a(i: Int)(implicit b: B): A = new A
  }

  import D._
  import K._

  123.foo
}
 */