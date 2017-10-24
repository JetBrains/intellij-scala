class ListLikeCollectionNodeRenderer {
  val name : SimpleMethodInvocationResult[_] = null
  name match {
    case Success(value: Boolean) => /*start*/value/*end*/
  }
  private class SimpleMethodInvocationResult[R]
  private case class Success[R](value: R) extends SimpleMethodInvocationResult[R]
}
//Boolean