class SimpleMethodScala {
  def <caret>bar(ii: Int, b: Boolean): Unit = {
    ii
  }
}

class SimpleMethodChild extends SimpleMethodScala {
  override def bar(ii: Int, b: Boolean): Unit = {
    ii
    super.bar(ii, true)
  }
}