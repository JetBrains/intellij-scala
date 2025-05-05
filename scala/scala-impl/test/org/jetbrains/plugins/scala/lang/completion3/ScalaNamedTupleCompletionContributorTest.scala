package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_5
))
class ScalaNamedTupleCompletionContributorTest extends ScalaCompletionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_3_5

  def testInUnitExpr(): Unit = doCompletionTest(
    fileText =
      s"""val x: (a: Int, b: String) = ($CARET)
         |""".stripMargin,
    resultText =
      s"""val x: (a: Int, b: String) = (a = ???, b = ???)
         |""".stripMargin,
    item = "a = ???, b = ???",
  )

  def testCompleteExistingNamedTuple(): Unit = doCompletionTest(
    fileText =
      s"""val x: (a: Int, b: String) = (a = 1, $CARET)
         |""".stripMargin,
    resultText =
      s"""val x: (a: Int, b: String) = (a = 1, b = ???)
         |""".stripMargin,
    item = "a = 1, b = ???",
  )

  def testCompleteExistingNamedTupleWithReordering(): Unit = doCompletionTest(
    fileText =
      s"""val x: (a: Int, b: String) = (b = "test", $CARET)
         |""".stripMargin,
    resultText =
      s"""val x: (a: Int, b: String) = (a = ???, b = "test")
         |""".stripMargin,
    item = """a = ???, b = "test"""",
  )

  def testCompleteExistingNamedTupleWithReordering2(): Unit = doCompletionTest(
    fileText =
      s"""val x: (a: Int, bbb: String) = (a = 1, b$CARET = "test")
         |""".stripMargin,
    resultText =
      s"""val x: (a: Int, bbb: String) = (a = 1, bbb = "test")
         |""".stripMargin,
    item = """a = 1, bbb = "test"""",
  )

  def testCompleteExistingNamedTupleWithReordering3(): Unit = doCompletionTest(
    fileText =
      s"""val x: (a: Int, bbb: String, ccc: Boolean) = (b$CARET = "test", a = 1, c = true)
         |""".stripMargin,
    resultText =
      s"""val x: (a: Int, bbb: String, ccc: Boolean) = (a = 1, bbb = "test", ccc = true)
         |""".stripMargin,
    item = """a = 1, bbb = "test", ccc = true""",
  )

  def testDontCompleteInExpr(): Unit =
    checkNoCompletion(s"val x: (a: Int, b: String) = (a = $CARET)") {
      item => item.getLookupString.contains(" = ")
    }

  // SCL-23823
  def testDontCompleteInExpr2(): Unit =
    checkNoCompletion(s"val x: (a: Int, b: String) = (a = p.$CARET, b = ???)") {
      item => item.getLookupString.contains(" = ")
    }

  def testDontCompleteIfAllComponentsAreThere(): Unit =
    checkNoCompletion(s"val x: (a: Int, b: String) = (a = ???, b$CARET = ???)") {
      item => item.getLookupString.contains(" = ")
    }
}
