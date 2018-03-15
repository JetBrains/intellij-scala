object Test {
  def update(index: Int, value: Int): Unit = { }

  def foo(): Unit = {
    this(<ref>index = 1) = 2
  }
}