def test() = {
  val message = "hello"
  throw new RuntimeException(mes/*caret*/)
}
//message