package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase.hasItemText
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.junit.Test

@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class Scala3OpaqueTypeAliasTest extends ScalaCompletionTestBase {

  @Test
  def testQualifierInside(): Unit = doCompletionTest(
    fileText =
      s"""object Inside:
         |  opaque type Foo = Int
         |  val foo: Foo = ???
         |  foo.ab$CARET""".stripMargin,
    resultText =
      s"""object Inside:
         |  opaque type Foo = Int
         |  val foo: Foo = ???
         |  foo.abs$CARET""".stripMargin,
    item = "abs"
  )

  @Test
  def testQualifierOutside(): Unit = checkNoCompletion(
    fileText =
      s"""object Inside:
         |  opaque type Foo = Int
         |object Outside:
         |  val foo: Inside.Foo = ???
         |  foo.ab$CARET""".stripMargin,
  )()

  @Test
  def testRenderingInside(): Unit = doRawCompletionTest(
    fileText =
      s"""object Inside:
         |  opaque type Foo = Int
         |  val x: Fo$CARET""".stripMargin,
    resultText =
      s"""object Inside:
         |  opaque type Foo = Int
         |  val x: Foo$CARET""".stripMargin,
  ) {
    hasItemText(_, "Foo")(typeText = "Int", itemTextBold = true)
  }

  @Test
  def testRenderingOutside(): Unit = doRawCompletionTest(
    fileText =
      s"""object Inside:
         |  opaque type Foo = Int
         |object Outside:
         |  val x: Inside.Fo$CARET""".stripMargin,
    resultText =
      s"""object Inside:
         |  opaque type Foo = Int
         |object Outside:
         |  val x: Inside.Foo$CARET""".stripMargin,
  ) {
    hasItemText(_, "Foo")(typeText = "", itemTextBold = true)
  }

  @Test
  def testSmartInside(): Unit = doCompletionTest(
    completionType = CompletionType.SMART,
    fileText =
      s"""object Inside:
         |  opaque type Foo = Int
         |  val foo: Foo = ???
         |  val x: Int = fo$CARET""".stripMargin,
    resultText =
      s"""object Inside:
         |  opaque type Foo = Int
         |  val foo: Foo = ???
         |  val x: Int = foo$CARET""".stripMargin,
    item = "foo"
  )

  @Test
  def testSmartOutside(): Unit = checkNoCompletion(
    `type` = CompletionType.SMART,
    fileText =
      s"""object Inside:
         |  opaque type Foo = Int
         |  val foo: Foo = ???
         |object Outside:
         |  val x: Int = Inside.fo$CARET""".stripMargin,
  )()
}
