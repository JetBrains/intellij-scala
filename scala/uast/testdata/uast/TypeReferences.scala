object Test {
  def foo(parameter: Int): String = {
    val varWithType: String = "Not Null"
    val varWithoutType = "lorem ipsum"
    var result = varWithType + varWithoutType
    return result
  }

  def parameterizedFoo[T](arg: T): Unit = {
    val a = arg
    val at: T = arg

    val tl: List[T] = List(at)
    val tsl: List[String] = null
    val lls: List[List[String]] = null
    val llt: List[List[T]] = null

    parameterizedFoo[List[String]](List.empty[String])

    ()
  }
}
