trait A[X] {
  def a: X
}

new A[String] {
  def a = /*start*/any/*end*/

  def any[X]: X = error("")
}

//Nothing