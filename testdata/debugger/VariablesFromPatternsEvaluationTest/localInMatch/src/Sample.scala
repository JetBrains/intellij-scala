object Sample {
  val name = "name"
  def main(args: Array[String]) {
    Option("a") match {
      case None =>
      case some @ Some(a) =>
        def foo(i: Int) {
          "stop here"
        }
        foo(10)
    }
  }
}