object Wrapper {
  val a: PartialFunction[Any, Boolean] = {
    case _ =>
      false
  }
}
//True