object SCL4559B {
  trait Z {
    type A
    type B

    implicit def a2s(a: A) = "text"
    implicit def b2s(b: B) = "text"

    trait ZZ1
    class ZZ2 extends ZZ1

    def foo(x: A, zz: ZZ1) = 1

    def foo(x: B, zz: ZZ2) = false
  }
  object Z extends Z {
    trait AA
    trait BB extends AA
    type A = AA
    type B = BB

    foo(new BB{}, new ZZ2)
  }

  import Z._

  val b : BB = new BB {}

  /*start*/b.contains("text")/*end*/
}
//Boolean