class ScalaImportClassFix {
  private def startOffset = 1
  def foo {
    val offset: Int = 1
    /*start*/offset >= startOffset/*end*/
  }
}
//Boolean