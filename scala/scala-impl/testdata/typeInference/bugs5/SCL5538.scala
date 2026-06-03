object SCL5538 {

  class A {
    def aMethod = 42
  }

  object B {
    trait C

    implicit def cToA(c: C): A = new A
  }

  class D extends B.C

  val d = new D

  /*start*/d.aMethod/*end*/
}
//Int