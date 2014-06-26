object StringPlusMethod {
  def error(message: String) = {
    val kind = "error: "
    /*start*/println(kind + message)/*end*/
  }

  def warning(message: String) = println("warning: " + message)
  def info(message: String) = println("info: " + message)
}
/*
object StringPlusMethod {
  def error(message: String) = {
    val kind = "error: "
    /*start*/testMethodName(kind, message)/*end*/
  }

  def testMethodName(kind: String, message: String) {
    println(kind + message)
  }

  def warning(message: String) = testMethodName("warning: ", message)
  def info(message: String) = testMethodName("info: ", message)
}
*/