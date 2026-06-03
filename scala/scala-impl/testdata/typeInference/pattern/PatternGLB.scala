class PatternGLB {
  class A
  class B extends A

  new B match {
    case d@(_: A) =>
      /*start*/d/*end*/
  }
}
//PatternGLB.this.B