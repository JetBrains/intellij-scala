object MyTest {
  object Test {
    protected def test(i: Int, a: Any): Unit = ???
    def test(i: Int, a: => Any): Unit = ???
  }
  Test.<ref>test(1, true)
}