package org.jetbrains.plugins.scala.lang.formatter.scalafmt

class ScalaFmtCommonTest extends ScalaFmtCommonTestBase

class ScalaFmtCommonTest_2_7 extends ScalaFmtCommonTestBase with UseConfig_2_7 {

  override def testCompleteFile(): Unit = {
    val before =
      """class T {
        |  def foo(a: Int, b: Int): Int = 42
        |  foo(
        |        42,
        |        43
        |  )
        |}
        |""".stripMargin
    val after =
      """class T {
        |  def foo(a: Int, b: Int): Int = 42
        |  foo(
        |    42,
        |    43
        |  )
        |}
        |""".stripMargin
    doTextTest(before, after)
  }

  override def testIncompleteFile(): Unit = {
    val before =
      """
        |def foo(a: Int, b: Int): Int = 42
        |foo(
        |        42,
        |        43
        |  )
        |""".stripMargin
    val after =
      """
        |def foo(a: Int, b: Int): Int = 42
        |foo(
        |  42,
        |  43
        |)
        |""".stripMargin
    doTextTest(before, after)
  }

  override def testIncompleteFile_1(): Unit = {
    val before =
      """
        |  def foo(a: Int, b: Int): Int = 42
        |  foo(
        |        42,
        |        43
        |  )
        |""".stripMargin
    //TODO the lacking of indent on the first line is from the test: the result gets trimmed
    val after =
      """
        |def foo(a: Int, b: Int): Int = 42
        |foo(
        |  42,
        |  43
        |)
        |""".stripMargin
    doTextTest(before, after)
  }

  override def testIncompleteFile_2(): Unit = {
    val before =
      """
        |def foo(a: Int, b: Int): Int = 42
        | foo(
        |   42,
        |   43
        | )
        |""".stripMargin
    val after =
      """
        |def foo(a: Int, b: Int): Int = 42
        |foo(
        |  42,
        |  43
        |)
        |""".stripMargin
    doTextTest(before, after)
  }
}

trait ScalaFmtCommonTestBase extends ScalaFmtTestBase {

  def testAddSpace(): Unit = {
    val before = "object O{}"
    val after = "object O {}\n"
    doTextTest(before, after)
  }

  def testReduceSpace(): Unit = {
    val before = "object        O {}"
    val after = "object O {}\n"
    doTextTest(before, after)
  }

  def testRemoveSpace(): Unit = {
    val before =
      """object O {
        |  def foo : Int = 42
        |}
        |""".stripMargin
    val after =
      """object O {
        |  def foo: Int = 42
        |}
        |""".stripMargin
    doTextTest(before, after)
  }

  def testWidenSpace(): Unit = {
    val before =
      """object O {
        |  42 match {
        |    case 1 => 1
        |    case 42 => 42
        |    case _ => 42
        |  }
        |}
        |""".stripMargin
    val after =
      """object O {
        |  42 match {
        |    case 1  => 1
        |    case 42 => 42
        |    case _  => 42
        |  }
        |}
        |""".stripMargin
    doTextTest(before, after)
  }

  def testAClass(): Unit = {
    val before =
      """class SmallTest {
        |def foo = {
        |println("ass")
        |42
        |}
        |}
        |""".stripMargin
    val after =
      """class SmallTest {
        |  def foo = {
        |    println("ass")
        |    42
        |  }
        |}
        |""".stripMargin
    doTextTest(before, after)
  }

  def testWidenSpace2(): Unit = {
    val before =
      """object O {
        | def foo: Int = 42
        |}
        |""".stripMargin
    val after =
      """object O {
        |  def foo: Int = 42
        |}
        |""".stripMargin
    doTextTest(before, after)
  }

  def testCompleteFile(): Unit = {
    val before =
      """class T {
        |  def foo(a: Int, b: Int): Int = 42
        |  foo(
        |        42,
        |        43
        |  )
        |}
        |""".stripMargin
    val after =
      """class T {
        |  def foo(a: Int, b: Int): Int = 42
        |  foo(42, 43)
        |}
        |""".stripMargin
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
        |""".stripMargin
    val after =
      """
        |def foo(a: Int, b: Int): Int = 42
        |foo(42, 43)
        |""".stripMargin
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
        |""".stripMargin
    //TODO the lacking of indent on the first line is from the test: the result gets trimmed
    val after =
      """
        |def foo(a: Int, b: Int): Int = 42
        |foo(42, 43)
        |""".stripMargin
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
        |""".stripMargin
    val after =
      """
        |def foo(a: Int, b: Int): Int = 42
        |foo(42, 43)
        |""".stripMargin
    doTextTest(before, after)
  }

  def testBug(): Unit = {
    val before =
      """
        |1
        |def foo(): Int = 3
        |""".stripMargin

    doTextTest(before)
  }

  def testBug1(): Unit =
    doTextTest(
      """
        |        1
        |    def foo(): Int = 3
        |""".stripMargin,
      """
        |1
        |def foo(): Int = 3
        |""".stripMargin
    )

  def testTopLevelObjectInpackage(): Unit = {
    val before =
      """package foo
        |object Scl4169 {
        |
        |  val b: Array[Any]={
        |
        |  List[Any]().toArray. map {case item => ""}
        |
        | }
        |
        |}
        |""".stripMargin
    val after =
      """package foo
        |object Scl4169 {
        |
        |  val b: Array[Any] = {
        |
        |    List[Any]().toArray.map { case item => "" }
        |
        |  }
        |
        |}
        |""".stripMargin
    doTextTest(before, after)
  }

  def testScl14133(): Unit = {
    val before =
      s"""
         |def x  =  42
         |//
         |""".stripMargin
    val after =
      s"""
         |def x = 42
         |//
         |""".stripMargin
    doTextTest(before, after)
  }

  def testCreateWorksheetFileAndFormat(): Unit = {
    doTextTest(
      """val    x=2+2
        |""".stripMargin,
      """val x = 2 + 2
        |""".stripMargin,
      "worksheet.sc"
    )
  }
}