object Sample {
  val name = "name"
  def main(args: Array[String]) {
    def printName(param: String, notUsed: String) {
      for (s <- List("a", "b"); if s == "a"; ss = s + s; i <- List(1,2); if i == 1; si = s + i) {
        val in = "in"
        println(s + param)
        "stop here"
      }
    }
    printName("param", "notUsed")
  }
}