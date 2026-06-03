class Color(d: Double)(foo: Boolean, x: Int) {
  def this(x: Int, y: Int) = this(1.23)(false, x)

  new Color(1.23)(true, 123)
}
