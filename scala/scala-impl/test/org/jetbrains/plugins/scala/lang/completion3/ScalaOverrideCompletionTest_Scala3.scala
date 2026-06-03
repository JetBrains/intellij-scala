package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}
import org.junit.Test

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest
))
class ScalaOverrideCompletionTest_Scala3 extends ScalaOverrideCompletionTestBase {

  override protected def prepareFileText(fileText: String): String =
    fileText.withNormalizedSeparator.trim

  @Test
  def testFunctionWithImplicitParameters(): Unit = doRawCompletionTest(
    fileText =
      s"""abstract class Base[T]:
         |  def myFunction(p: String, p2: T)(implicit c: CharSequence, t: T): String
         |
         |class Child extends Base[Short]:
         |  override def myFunction$CARET
         |""".stripMargin,
    resultText =
      s"""abstract class Base[T]:
         |  def myFunction(p: String, p2: T)(implicit c: CharSequence, t: T): String
         |
         |class Child extends Base[Short]:
         |  override def myFunction(p: String, p2: Short)(implicit c: CharSequence, t: Short): String = $CARET$START???$END
         |""".stripMargin
  )()

  @Test
  def testFunctionWithUsingParameters(): Unit = doRawCompletionTest(
    fileText =
      s"""abstract class Base[T]:
         |  def myFunction(p: String, p2: T)(using c: CharSequence, t: T): String
         |
         |class Child extends Base[Short]:
         |  override def myFunction$CARET
         |""".stripMargin,
    resultText =
      s"""abstract class Base[T]:
         |  def myFunction(p: String, p2: T)(using c: CharSequence, t: T): String
         |
         |class Child extends Base[Short]:
         |  override def myFunction(p: String, p2: Short)(using c: CharSequence, t: Short): String = $CARET$START???$END
         |""".stripMargin
  )()

  @Test
  def testExtension(): Unit = doRawCompletionTest(
    fileText =
      s"""abstract class MyBaseFromScala3:
         |  extension (target: String)(using c1: Char)
         |    def myExt1(p: String)(using c2: CharSequence): String
         |    def myExt2(p: String): String
         |
         |class MyChildInScala3 extends MyBaseFromScala3:
         |  overrext1$CARET
         |""".stripMargin,
    resultText =
      s"""abstract class MyBaseFromScala3:
         |  extension (target: String)(using c1: Char)
         |    def myExt1(p: String)(using c2: CharSequence): String
         |    def myExt2(p: String): String
         |
         |class MyChildInScala3 extends MyBaseFromScala3:
         |  extension (target: String)(using c1: Char) override def myExt1(p: String)(using c2: CharSequence): String = $CARET$START???$END
         |""".stripMargin
  )()

  @Test
  def testExtension_ImplementedInNestedIndentationBasedSyntax(): Unit = doRawCompletionTest(
    fileText =
      s"""abstract class MyBaseFromScala3:
         |  extension (target: String)(using c1: Char)
         |    def myExt1(p: String)(using c2: CharSequence): String
         |    def myExt2(p: String): String
         |
         |object wrapper1:
         |  object wrapper2:
         |    object wrapper3:
         |      class MyChildInScala3 extends MyBaseFromScala3:
         |        overrext1$CARET
         |  """.stripMargin,
    resultText =
      s"""abstract class MyBaseFromScala3:
         |  extension (target: String)(using c1: Char)
         |    def myExt1(p: String)(using c2: CharSequence): String
         |    def myExt2(p: String): String
         |
         |object wrapper1:
         |  object wrapper2:
         |    object wrapper3:
         |      class MyChildInScala3 extends MyBaseFromScala3:
         |        extension (target: String)(using c1: Char) override def myExt1(p: String)(using c2: CharSequence): String = $CARET$START???$END
         |""".stripMargin
  )()
}
