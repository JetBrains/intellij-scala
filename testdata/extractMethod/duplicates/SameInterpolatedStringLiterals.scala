object SameInterpolatedStringLiterals {

  def log(message: String, level: Int) {
    level match {
      case 0 =>
        /*start*/println(s"info: $level")
        println(message)/*end*/
      case 1 =>
        val level2 = level
        println(s"info: $level2")
        println(message)
    }
  }
}
/*
object SameInterpolatedStringLiterals {

  def log(message: String, level: Int) {
    level match {
      case 0 =>
        /*start*/testMethodName(level, message)/*end*/
      case 1 =>
        val level2 = level
        testMethodName(level2, message)
    }
  }

  def testMethodName(level: Int, message: String): Unit = {
    println(s"info: $level")
    println(message)
  }
}
*/