object NokFunction {
  def foo(): Unit = {
    type M = List[Int]
    val t: /*begin*/M/*end*/ = List(56)
  }
}

