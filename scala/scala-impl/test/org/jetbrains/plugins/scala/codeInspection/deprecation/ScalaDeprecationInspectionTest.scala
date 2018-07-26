package org.jetbrains.plugins.scala.codeInspection.deprecation

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

class ScalaDeprecationInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[ScalaDeprecationInspection]
  override protected val description: String = "is deprecated"
  override protected def descriptionMatches(s: String) = s != null && s.contains(description)

  def testDeprecatedParamName(): Unit = {
    val code =
      s"""
         |def inc(x: Int, @deprecatedName('y, "FooLib 12.0") n: Int): Int = x + n
         |inc(1, ${START}y$END = 2)
       """.stripMargin
    checkTextHasError(code)
  }

  def testDeprecatedImport(): Unit = {
    val code =
      s"""
         |@deprecated("Deprecated", "0.0.1")
         |object Util { }
         |
         |import ${START}Util$END._
       """.stripMargin
    checkTextHasError(code)
  }

  def testDeprecatedMethods(): Unit = {
    val code =
      s"""
         |trait T[A] {
         |  @deprecated("Use bar()", "1.0")
         |  def foo(): Unit
         |}
         |
         |object Test {
         |  val t: T[Int] = ???
         |  t.${START}foo$END()
         |}
       """.stripMargin
    checkTextHasError(code)
  }

  def testConstructorOfDeprecatedClass(): Unit = {
    val commonCode =
      """
        |@deprecated("Use Bar", "0.1.0")
        |class Foo(x: Int)
      """.stripMargin

    val primaryConstructor =
      s"""
         |$commonCode
         |object Test {
         |  val f = new ${START}Foo$END(123)
         |}
       """.stripMargin
    checkTextHasError(primaryConstructor)

    val nonPrimaryConstructor =
      s"""
         |$commonCode {
         |  def this() = this(123)
         |}
         |object Test {
         |  val f = new ${START}Foo$END()
         |}
       """.stripMargin
    checkTextHasError(nonPrimaryConstructor)

    val methodOfDeprecatedClass =
      s"""
         |$commonCode {
         |  def methodOfDeprecatedClass(n: Int): Unit = println(n)
         |
         |  methodOfDeprecatedClass(123)
         |}
       """.stripMargin
    checkTextHasNoErrors(methodOfDeprecatedClass)
  }
}
