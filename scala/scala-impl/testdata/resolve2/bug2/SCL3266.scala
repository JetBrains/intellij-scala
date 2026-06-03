object O extends App {
  def test(name: String)(age: Int) {}
  test(name = "xxx")(/* */age = 10)
}