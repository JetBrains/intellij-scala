package org.jetbrains.plugins.scala.lang.formatter.tests

class ScalaFmtSelectionTest extends SelectionTest {
  override def setUp(): Unit = {
    super.setUp()
    getScalaSettings.USE_SCALAFMT_FORMATTER = true
  }

  def testExprSelection(): Unit = {
    val before =
      s"class Test { val v = ${startMarker}1    +     22  $endMarker}"
    val after = "class Test { val v = 1 + 22 }"
    doTextTest(before, after)
  }

  def testStatementSelection(): Unit = {
    val before =
      s"""
         |class Test {
         |  def foo(): Unit = {
         |    ${startMarker}println(42    +   22)$endMarker
         |  }
         |}
      """.stripMargin
    val after =
      """
        |class Test {
        |  def foo(): Unit = {
        |    println(42 + 22)
        |  }
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testPreserveBadFormatting(): Unit = {
    val before =
      s"""
         |class Test {
         |  def foo(): Unit = {
         |    ${startMarker}pri${endMarker}ntln(42   +   2)
         |  }
         |}
       """.stripMargin
    val after =
      """
        |class Test {
        |  def foo(): Unit = {
        |    println(42   +   2)
        |  }
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testProperRangeWidening(): Unit = {
    val before =
      s"""
         |class Test {
         |  def foo(): Unit = {
         |    println( 42 $startMarker +  43  +  28$endMarker )
         |  }
         |}
       """.stripMargin
    val after =
      """
        |class Test {
        |  def foo(): Unit = {
        |    println( 42 + 43 + 28)
        |  }
        |}
      """.stripMargin
    doTextTest(before, after)
  }
}
