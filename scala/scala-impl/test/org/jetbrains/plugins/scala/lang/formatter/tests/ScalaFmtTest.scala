package org.jetbrains.plugins.scala.lang.formatter.tests

import org.jetbrains.plugins.scala.lang.formatter.AbstractScalaFormatterTestBase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

class ScalaFmtTest extends AbstractScalaFormatterTestBase {

  override def setUp(): Unit = {
    super.setUp()
    getScalaSettings.FORMATTER = ScalaCodeStyleSettings.SCALAFMT_FORMATTER
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

  def testCompleteFile(): Unit = {
    val before =
      """
        |class T {
        |  def foo(a: Int, b: Int): Int = 42
        |  foo(
        |        42,
        |        43
        |  )
        |}
      """.stripMargin
    val after =
      """
        |class T {
        |  def foo(a: Int, b: Int): Int = 42
        |  foo(42, 43)
        |}
      """.stripMargin
    doTextTest(before, after)
  }


  def testIncompleteFile(): Unit = {
    val before =
      """
        |def foo(a: Int, b: Int): Int = 42
        |foo(
        |        42,
        |        43
        |  )
      """.stripMargin
    val after =
      """
        |def foo(a: Int, b: Int): Int = 42
        |foo(42, 43)
      """.stripMargin
    doTextTest(before, after)
  }

  def testIncompleteFile_1(): Unit = {
    val before =
      """
        |  def foo(a: Int, b: Int): Int = 42
        |  foo(
        |        42,
        |        43
        |  )
      """.stripMargin
    //TODO the lacking of indent on the first line is from the test: the result gets trimmed
    val after =
      """
        |def foo(a: Int, b: Int): Int = 42
        |foo(42, 43)
      """.stripMargin
    doTextTest(before, after)
  }

  def testIncompleteFile_2(): Unit = {
    val before =
      """
        |def foo(a: Int, b: Int): Int = 42
        | foo(
        |   42,
        |   43
        | )
      """.stripMargin
    val after =
      """
        |def foo(a: Int, b: Int): Int = 42
        |foo(42, 43)
      """.stripMargin
    doTextTest(before, after)
  }

  def testBug(): Unit = {
    val before =
      """
        |1
        |def foo(): Int = 3
      """.stripMargin

    doTextTest(before)
  }

  def testTopLevelObjectInpackage(): Unit = {
    val before =
      """
        |package foo
        |object Scl4169 {
        |
        |  val b: Array[Any]={
        |
        |  List[Any]().toArray. map {case item => ""}
        |
        | }
        |
        |}
      """.stripMargin
    val after =
      """
        |package foo
        |object Scl4169 {
        |
        |  val b: Array[Any] = {
        |
        |    List[Any]().toArray.map { case item => "" }
        |
        |  }
        |
        |}
      """.stripMargin
    doTextTest(before, after)
  }
}
