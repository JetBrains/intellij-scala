object Sample {
  def main(args: Array[String]) {
    val jc = new test.JavaClass()
    import jc._
    import test.JavaClass._

    val inner = new JavaInner()
    import inner._

    "stop here"
  }
}