class Test {
  def ccc: Int = 42
}

trait foo { self: Test =>
  def bar = {
    <ref>ccc
  }
}
