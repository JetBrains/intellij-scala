object Sample {
  val name = "name"
  def main(args: Array[String]) {
    def printName(param: String, notUsed: String) {
      List("a").foreach {
        a =>
            val x = "x"
            println(a + param)
            "stop here"
      }
    }
    printName("param", "notUsed")
  }
}