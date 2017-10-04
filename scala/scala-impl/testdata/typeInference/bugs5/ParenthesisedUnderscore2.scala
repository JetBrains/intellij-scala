object ParenthesisedUnderscore2 {
  class A {
    def foo() = 123
    def foo(x: Int) = 123
  }
  val x: List[A] = List.empty

  /*start*/x map (_.foo)/*end*/
}
//List[Int]