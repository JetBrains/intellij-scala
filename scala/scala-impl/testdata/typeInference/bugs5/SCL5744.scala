object SCL5744 {
  class B {
    def foo = 1
  }
  class R {
    def goo = 2
  }
  class K

  object K {
    implicit def a2r[T](a: Z.U[T]): R = new R
  }

  trait ZZ {
    class U[T]
  }
  object Z extends ZZ {
    implicit def a2b[T](a: U[T]): B = new B
  }

  class G extends Z.U[K]

  object Main {
    val g = new G
    /*start*/(g.foo, g.goo)/*end*/
  }
}
//(Int, Int)