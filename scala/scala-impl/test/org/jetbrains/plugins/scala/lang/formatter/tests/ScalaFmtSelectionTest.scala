package org.jetbrains.plugins.scala.lang.formatter.tests

class ScalaFmtSelectionTest extends SelectionTest {
  override def setUp(): Unit = {
    super.setUp()
    getScalaSettings.USE_SCALAFMT_FORMATTER = true
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
}
