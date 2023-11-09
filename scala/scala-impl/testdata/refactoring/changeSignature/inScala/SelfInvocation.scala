class <caret>Color(x: Int, foo: Boolean) {
  def this(x: Int, y: Int) = this(x, false)

  new Color(123, true)
}
