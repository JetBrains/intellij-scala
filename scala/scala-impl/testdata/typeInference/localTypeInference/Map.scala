class ScalaTest(val num: Int) {
  def printSomething(text: String) {
    println(text + " " + num)
  }
}
val map = Map(1 -> new ScalaTest(1))
/*start*/map(1)/*end*/
//ScalaTest