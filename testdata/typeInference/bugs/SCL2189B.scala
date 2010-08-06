object test {
  trait B
  trait A[X] {
    self: B =>

    /*start*/ self /*end*/
  }
}
//A[X] with test.B