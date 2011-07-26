object test {
  trait B
  trait A[X] {
    self: B =>

    /*start*/ self /*end*/
  }
}
//test.A[X] with test.B