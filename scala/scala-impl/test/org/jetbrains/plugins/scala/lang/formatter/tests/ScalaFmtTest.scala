package org.jetbrains.plugins.scala.lang.formatter.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase

class ScalaFmtTest extends AbstractScalaFormatterTestBase {

  override def setUp(): Unit = {
    super.setUp()
    getScalaSettings.USE_SCALAFMT_FORMATTER = true
  }

  def testAddSpace(): Unit = {
    val before = "object O{}"
    val after = "object O {}"
    doTextTest(before, after)
  }

  def testReduceSpace(): Unit = {
    val before = "object        O {}"
    val after = "object O {}"
    doTextTest(before, after)
  }

  def testRemoveSpace(): Unit = {
    val before =
      """
        |object O {
        |  def foo : Int = 42
        |}
      """.stripMargin
    val after =
      """
        |object O {
        |  def foo: Int = 42
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testWidenSpace(): Unit = {
    val before =
      """
        |object O {
        |  42 match {
        |    case 1 => 1
        |    case 42 => 42
        |    case _ => 42
        |  }
        |}
      """.stripMargin
    val after =
      """
        |object O {
        |  42 match {
        |    case 1  => 1
        |    case 42 => 42
        |    case _  => 42
        |  }
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testAClass(): Unit = {
    val before =
      """
        |class SmallTest {
        |def foo = {
        |println("ass")
        |42
        |}
        |}
      """.stripMargin
    val after =
      """
        |class SmallTest {
        |  def foo = {
        |    println("ass")
        |    42
        |  }
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testWidenSpace2(): Unit = {
    val before =
      """
        |object O {
        | def foo: Int = 42
        |}
      """.stripMargin
    val after =
      """
        |object O {
        |  def foo: Int = 42
        |}
      """.stripMargin
    doTextTest(before, after)
  }

  def testWidenSpace3(): Unit = {
    val before =
      """
        | def foo: Int = 42
      """.stripMargin
    val after =
      """
        |  def foo: Int = 42
      """.stripMargin
    doTextTest(before, after)
  }
}
