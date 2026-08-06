class SCL5250 {
  var _foo = 3
  def foo = _foo

  def foo_=(f: Int): Boolean = {
    _foo = f
    true
  }

  def main(args: Array[String]) {
    println(foo) // 3
    if (foo = 32) {
      println(foo) // 32
    }
    if (foo += 1) { // red underline from Intellij Scala plugin
      println(foo)  // 33
    }
    if (foo -= 4) { // red underline from Intellij Scala plugin
      println(foo)  // 29
    }

    /*start*/(foo = 32, foo += 1, foo -= 4)/*end*/
  }
}
//(Boolean, Boolean, Boolean)