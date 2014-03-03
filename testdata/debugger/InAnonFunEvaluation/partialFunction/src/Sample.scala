object Sample {
  val name = "name"
  def main(args: Array[String]) {
    def printName(param: String, notUsed: String) {
      List(("a", 10)).foreach {
        case (a, i: Int) =>
            val x = "x"
            println(a + param)
            "stop here"
      }
    }
    printName("param", "notUsed")
  }
}