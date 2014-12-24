object DifferentLiterals {

  def log(message: String, level: Int) {
    level match {
      case 0 =>
        /*start*/println("info: ")
        println(message)/*end*/
      case 1 =>
        println("warning: ")
        println(message)
    }
  }
}
/*
object DifferentLiterals {

  def log(message: String, level: Int) {
    level match {
      case 0 =>
        /*start*/testMethodName(message)/*end*/
      case 1 =>
        println("warning: ")
        println(message)
    }
  }

  def testMethodName(message: String): Unit = {
    println("info: ")
    println(message)
  }
}
*/