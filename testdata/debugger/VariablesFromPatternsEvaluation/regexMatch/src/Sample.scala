object Sample {
  val name = "name"
  def main(args: Array[String]) {
    val Decimal = "(-)?(\\d+)(\\.\\d*)?".r
    "-2.5" match {
      case number @ Decimal(sign, _, dec) =>
        "stop here"
      case _ =>
    }
  }
}