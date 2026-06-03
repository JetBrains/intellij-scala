class Test {
  def test(smth: String, is: Int*): Unit = {
    def local(i: Int, s: Boolean = true) = {
      i + 1
    }

    local(1)
  }
}