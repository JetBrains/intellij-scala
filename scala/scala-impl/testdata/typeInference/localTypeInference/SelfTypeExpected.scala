class SelfInvocationExpected(x: Map[Int, Int]) {
  def this() {
    this(/*start*/Map.empty/*end*/)
  }
}
//Map[Int, Nothing]