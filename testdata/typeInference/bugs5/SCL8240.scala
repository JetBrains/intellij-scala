object SCL8240 {

  trait A {

    trait B

    def c: B
  }

  trait D

  trait E {
    val f: A with D

    def foo(b: f.B) = 1

    def foo(s: String) = "1"

    /*start*/ foo(f.c)
    /*end*/
    val g: f.B = f.c
  }

}
//Int