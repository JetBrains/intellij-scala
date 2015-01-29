class A {
  class F {
    def foo(r : A.this.R) = {
      /*start*/r/*end*/
    }
  }

  class A
  type R = Int
}
//A.this.type#R