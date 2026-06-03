def test() = {
  val message = "hello"
  throw new RuntimeException(mess/*caret*/)
}
//message