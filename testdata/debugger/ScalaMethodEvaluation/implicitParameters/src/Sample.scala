object Sample {
  def moo(x: Int)(implicit s: String) = x + s.length()
  def foo(x: Int)(implicit y: Int) = {
    implicit val s = "test"
    "stop here"
    x + y
  }
  def main(args: Array[String]) {
    implicit val x = 1
    foo(1)
  }
}