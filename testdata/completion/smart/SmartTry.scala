object SmartTry {
  val intoint = 34
  val x: Int = {
    try {
      int/*caret*/
    } finally {

    }
  }
}
//intoint