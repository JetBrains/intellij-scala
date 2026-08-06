class Test {
  def test(smth: String, is: Int*): Unit = {
    def <caret>inner(i: Int): Int = {
      i + 1
    }

    inner(1)
  }
}