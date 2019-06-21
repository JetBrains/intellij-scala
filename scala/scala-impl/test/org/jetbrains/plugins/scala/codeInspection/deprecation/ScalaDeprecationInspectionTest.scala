package org.jetbrains.plugins.scala.codeInspection.deprecation

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.debugger.{ScalaVersion, Scala_2_12}

abstract class ScalaDeprecationInspectionTestBase extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[ScalaDeprecationInspection]
  override protected val description: String = "is deprecated"
  override protected def descriptionMatches(s: String) = s != null && s.contains(description)
}

class ScalaDeprecationInspectionTest extends ScalaDeprecationInspectionTestBase {

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

  def test_apply_on_case_class(): Unit = checkTextHasError(
    s"""
      |@deprecated
      |case class Test(i: Int)
      |${START}Test$END(29)
    """.stripMargin
  )

  // this case should not have errors because the creation of the case class is
  // is already reported
  def test_unapply_on_case_class(): Unit = checkTextHasNoErrors(
    """
      |@deprecated
      |case class Test(i: Int)
      |
      |null match {
      |  case Test(2) =>
      |}
    """.stripMargin
  )

  def test_apply(): Unit = checkTextHasError(
    s"""
      |object Test {
      |  @deprecated
      |  def apply() = ()
      |}
      |
      |${START}Test$END()
    """.stripMargin
  )

  def test_apply_on_deprecated(): Unit = checkTextHasError(
    s"""
       |@deprecated
       |object Test {
       |  def apply() = ()
       |}
       |
       |${START}Test$END()
    """.stripMargin
  )

  def test_unapply(): Unit = checkTextHasError(
    s"""
      |object Test {
      |  @deprecated
      |  def unapply(i: Int) = Some(i)
      |}
      |
      |3 match {
      |  case ${START}Test$END(x) =>
      |}
    """.stripMargin
  )

  def test_unapply_on_deprecated(): Unit = checkTextHasError(
    s"""
      |@deprecated
      |object Test {
      |  def unapply(i: Int) = Some(i)
      |}
      |
      |3 match {
      |  case ${START}Test$END(x) =>
      |}
    """.stripMargin
  )

  def test_update(): Unit = checkTextHasError(
    s"""
       |object Test {
       |  @deprecated
       |  def update(i: Int, i2: Int) = ()
       |}
       |
      |${START}Test$END(3) = 3
    """.stripMargin
  )

  def test_update_on_deprecated(): Unit = checkTextHasError(
    s"""
      |@deprecated
      |object Test {
      |  def update(i: Int, i2: Int) = ()
      |}
      |
      |${START}Test$END(3) = 3
    """.stripMargin
  )

  def test_method_on_deprecated_class(): Unit = checkTextHasError(
    s"""
      |@deprecated
      |class Test {
      |  def test(): Unit = ()
      |}
      |
      |val x: ${START}Test$END = null
      |x.test()
    """.stripMargin
  )
}

class ScalaDeprecationInspectionTest_2_12 extends ScalaDeprecationInspectionTestBase {

  override implicit val version: ScalaVersion = Scala_2_12

  def testDeprecatedParamName(): Unit = {
    val code =
      s"""
         |def inc(x: Int, @deprecatedName('y, "FooLib 12.0") n: Int): Int = x + n
         |inc(1, ${START}y$END = 2)
       """.stripMargin
    checkTextHasError(code)
  }

}