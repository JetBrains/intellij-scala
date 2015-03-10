object DifferentInterpolatedStringLiterals {

  def log(message: String, level: Int) {
    level match {
      case 0 =>
        /*start*/println(s"info: $level")
        println(message)/*end*/
      case 1 =>
        println(s"warning: $level")
        println(message)
    }
  }
}
/*
object DifferentInterpolatedStringLiterals {

  def log(message: String, level: Int) {
    level match {
      case 0 =>
        /*start*/testMethodName(level, message)/*end*/
      case 1 =>
        println(s"warning: $level")
        println(message)
    }
  }

  def testMethodName(level: Int, message: String): Unit = {
    println(s"info: $level")
    println(message)
  }
}
 */