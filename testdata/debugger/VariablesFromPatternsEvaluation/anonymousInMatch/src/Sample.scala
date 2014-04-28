object Sample {
  val name = "name"
  def main(args: Array[String]) {
    Option("a") match {
      case None =>
      case some @ Some(a) =>
        List(10) foreach { i =>
          "stop here"
        }
    }
  }
}