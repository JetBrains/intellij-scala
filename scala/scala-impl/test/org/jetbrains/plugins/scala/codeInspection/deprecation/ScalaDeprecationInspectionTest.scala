package org.jetbrains.plugins.scala
package codeInspection
package deprecation

import com.intellij.codeInspection.LocalInspectionTool
import org.intellij.lang.annotations.Language

abstract class ScalaDeprecationInspectionTestBase extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] = classOf[ScalaDeprecationInspection]
  override protected val description: String = "is deprecated"
  override protected def descriptionMatches(s: String) = s != null && s.contains(description)
}

class ScalaDeprecationInspectionTest extends ScalaDeprecationInspectionTestBase {

  private def addJavaClass(@Language("JAVA") text: String): Unit = getFixture.addClass(text)

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

  def test_overriding_deprecated_method(): Unit = checkTextHasError{
    s"""class Base() {
       |  @deprecated()
       |  def foo(x: Int): String = ???
       |  def foo(s: String): String = ???
       |}
       |class Child extends Base {
       |  override def ${START}foo$END(x: Int): String = ???
       |  override def foo(s: String): String = ???
       |}
       |""".stripMargin
  }

  def test_extending_deprecated_class(): Unit = checkTextHasError{
    s"""@deprecated()
       |class Base()
       |class Child extends ${START}Base$END {}
       |""".stripMargin
  }

  def test_extending_deprecated_trait(): Unit = checkTextHasError{
    s"""@deprecated()
       |trait Base()
       |class Child extends ${START}Base$END {}
       |""".stripMargin
  }

  def test_extending_deprecated_class_and_trait(): Unit = checkTextHasError{
    s"""@deprecated()
       |class BaseClass()
       |@deprecated()
       |trait BaseTrait()
       |class Child extends ${START}BaseClass$END with ${START}BaseTrait$END {}
       |""".stripMargin
  }

  def test_overriding_deprecated_method_from_java_class(): Unit = {
    addJavaClass(
      """import java.lang.Deprecated;
        |class Base() {
        |  @Deprecated
        |  String foo(int x) { return null; }
        |  String foo(String s) { return null; }
        |}
        |""".stripMargin
    )
    checkTextHasError{
      s"""class Child extends Base {
         |  override def ${START}foo$END(x: Int): String = ???
         |  override def foo(s: String): String = ???
         |}
         |""".stripMargin
    }
  }

  def test_extending_deprecated_java_class(): Unit = {
    addJavaClass(
      """import java.lang.Deprecated;
        |@Deprecated
        |class Base {}
        |""".stripMargin
    )
    checkTextHasError{
      s"""class Child extends ${START}Base$END {}""".stripMargin
    }
  }

  def test_extending_deprecated_java_interface(): Unit = {
    addJavaClass(
      """import java.lang.Deprecated;
        |@Deprecated
        |interface Base {}
        |""".stripMargin
    )
    checkTextHasError{
      s"""@deprecated()
         |trait Base()
         |class Child extends ${START}Base$END {}
         |""".stripMargin
    }
  }

  def test_extending_deprecated_java_class_and_java_trait(): Unit = {
    addJavaClass(
      """import java.lang.Deprecated;
        |@Deprecated
        |class BaseClass {}
        |""".stripMargin
    )
    addJavaClass(
      """import java.lang.Deprecated;
        |@Deprecated
        |interface BaseInterface {}
        |""".stripMargin
    )
    checkTextHasError{
      s"""class Child extends ${START}BaseClass$END with ${START}BaseInterface$END {}""".stripMargin
    }
  }

  def testDeprecatedParamName(): Unit = {
    val code =
      s"""
         |def inc(x: Int, @deprecatedName("y", "FooLib 12.0") n: Int): Int = x + n
         |inc(1, ${START}y$END = 2)
       """.stripMargin
    checkTextHasError(code)
  }

  def testDeprecatedOverriding(): Unit = {
    checkTextHasNoErrors(
      s"""
         |trait Base {
         |  @deprecatedOverriding
         |  def test(): Int = 3
         |}
         |class Child extends Base {
         |  this.test()
         |}
         |
         |val x = new Child
         |x.test()
         |""".stripMargin
    )

    checkTextHasError(
      s"""
         |trait Base {
         |  @deprecatedOverriding
         |  def test(): Int = 3
         |}
         |class Child extends Base {
         |  def ${START}test$END(): Int = 4
         |}
         |""".stripMargin
    )
  }

  def testUnusedDeprecatedInheritance(): Unit = checkTextHasNoErrors(
    s"""
       |@deprecatedInheritance
       |class Test {
       |  def test(): Int = 3
       |}
       |
       |val x = new Test
       |x.test()
       |""".stripMargin
  )

  def testDeprecatedInheritanceWithoutConstructorCall(): Unit = checkTextHasError(
    s"""
       |@deprecatedInheritance
       |class Base {
       |  def test(): Int = 3
       |}
       |
       |class Impl extends ${START}Base$END {
       |  override test(): Int = 4
       |}
       |""".stripMargin
  )

  def testDeprecatedInheritanceWithConstructorCall(): Unit = checkTextHasError(
    s"""
       |@deprecatedInheritance
       |class Base(i: Int) {
       |  def test(): Int = 3
       |}
       |
       |class Impl extends ${START}Base$END(666) {
       |  override test(): Int = 4
       |}
       |""".stripMargin
  )

  def testDeprecatedInheritanceInNewExpr(): Unit = checkTextHasError(
    s"""
       |@deprecatedInheritance
       |class Base {
       |  def test(): Int = 3
       |}
       |
       |new ${START}Base$END {
       |  override test(): Int = 4
       |}
       |""".stripMargin
  )
}

class ScalaDeprecationInspectionTest_where_deprecatedName_symbol_constructor_is_deprecated extends ScalaDeprecationInspectionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version  <= LatestScalaVersions.Scala_2_12

  def testDeprecatedParamName(): Unit = {
    val code =
      s"""
         |def inc(x: Int, @deprecatedName('y, "FooLib 12.0") n: Int): Int = x + n
         |inc(1, ${START}y$END = 2)
       """.stripMargin
    checkTextHasError(code)
  }

}