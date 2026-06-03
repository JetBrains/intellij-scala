class Kate {
  def myPrint(t: Int) {
  }
}

object StaticMethodParam {
  val qqq = 3
  def foo (): Unit = {
    val int = 34
    (new Kate).myPrint(<caret>)
  }
}

