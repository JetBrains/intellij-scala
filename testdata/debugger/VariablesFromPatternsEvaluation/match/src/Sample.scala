object Sample {
  val name = "name"
  def main(args: Array[String]) {
    val x = (List(1, 2), Some("z"), None)
    x match {
      case all @ (list @ List(q, w), some @ Some(z), _) =>
        "stop here"
      case _ =>
    }
  }
}