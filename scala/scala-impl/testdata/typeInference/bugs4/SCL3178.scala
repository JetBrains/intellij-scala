trait Base {
  def getID: String
}

abstract class X extends Base {
  override def getID: String = ""

  private[this] def bar = {
    def foo = /*start*/true/*end*/
    ""
  }
}
()
//expected: <none>