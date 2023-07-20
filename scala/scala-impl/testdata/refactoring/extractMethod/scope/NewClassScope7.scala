class MyClass {
/*inThisScope*/
  def foo(): Seq[String] = {
    val result: Seq[String] = ???

    /*start*/val value = false
    var variable = false

    result.foreach { x =>
      println(value)
      println(variable)
      variable = true
    }/*end*/

    result
  }
}
/*
class MyClass {

  def foo(): Seq[String] = {
    val result: Seq[String] = ???

    testMethodName(result)

    result
  }

  def testMethodName(result: Seq[String]): Unit = {
    val value = false
    var variable = false

    result.foreach { x =>
      println(value)
      println(variable)
      variable = true
    }
  }
}
*/