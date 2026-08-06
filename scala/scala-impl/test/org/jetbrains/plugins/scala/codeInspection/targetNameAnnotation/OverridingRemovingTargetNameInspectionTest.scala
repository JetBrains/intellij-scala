package org.jetbrains.plugins.scala.codeInspection.targetNameAnnotation

import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class OverridingRemovingTargetNameInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection = classOf[OverridingRemovingTargetNameInspection]
  override protected val description = OverridingRemovingTargetNameInspection.message("testExtName")

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  private val hint = ScalaInspectionBundle.message("add.targetname.annotation")

  def testOverrideDef(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class A:
         |  @targetName("testExtName")
         |  def foo: Int = 1
         |
         |class B extends A:
         |  override def ${START}foo$END: Int = 2
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |class A:
        |  @targetName("testExtName")
        |  def foo: Int = 1
        |
        |class B extends A:
        |  @targetName("testExtName")
        |  override def foo: Int = 2
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testOverrideDefToVal(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class A:
         |  @targetName("testExtName")
         |  def foo: Int = 1
         |
         |class B extends A:
         |  override val ${START}foo$END: Int = 2
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |class A:
        |  @targetName("testExtName")
        |  def foo: Int = 1
        |
        |class B extends A:
        |  @targetName("testExtName")
        |  override val foo: Int = 2
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testOverrideVal(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class A:
         |  @targetName("testExtName")
         |  val foo: Int = 1
         |
         |class B extends A:
         |  override val ${START}foo$END: Int = 2
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |class A:
        |  @targetName("testExtName")
        |  val foo: Int = 1
        |
        |class B extends A:
        |  @targetName("testExtName")
        |  override val foo: Int = 2
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testOverrideVar(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class A:
         |  @targetName("testExtName")
         |  var foo: Int = 1
         |
         |class B extends A:
         |  override var ${START}foo$END: Int = 2
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |class A:
        |  @targetName("testExtName")
        |  var foo: Int = 1
        |
        |class B extends A:
        |  @targetName("testExtName")
        |  override var foo: Int = 2
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testOverrideClassParam(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class A(@targetName("testExtName") val foo: Int)
         |
         |class B(override val ${START}foo$END: Int) extends A
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |class A(@targetName("testExtName") val foo: Int)
        |
        |class B(@targetName("testExtName") override val foo: Int) extends A
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testOverrideValToClassParam(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class A:
         |  @targetName("testExtName")
         |  val foo: Int = 1
         |
         |class B(override val ${START}foo$END: Int) extends A
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |class A:
        |  @targetName("testExtName")
        |  val foo: Int = 1
        |
        |class B(@targetName("testExtName") override val foo: Int) extends A
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testOverrideTypeAlias(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |trait A:
         |  @targetName("testExtName")
         |  type Foo
         |
         |class B extends A:
         |  override type ${START}Foo$END = Long
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |trait A:
        |  @targetName("testExtName")
        |  type Foo
        |
        |class B extends A:
        |  @targetName("testExtName")
        |  override type Foo = Long
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testFixAll(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class A:
         |  @targetName("testExtName")
         |  def foo: Int = 1
         |  def bar: String = "..."
         |  @targetName("bazExt")
         |  def baz: Boolean = false
         |
         |class B extends A:
         |  override def ${START}foo$END: Int = 2
         |  override def bar: String = "???"
         |  @targetName("bazExt")
         |  override def baz: Boolean = true
         |
         |object O:
         |  object C extends A:
         |    override def ${START}foo$END: Int = 3
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |class A:
        |  @targetName("testExtName")
        |  def foo: Int = 1
        |  def bar: String = "..."
        |  @targetName("bazExt")
        |  def baz: Boolean = false
        |
        |class B extends A:
        |  @targetName("testExtName")
        |  override def foo: Int = 2
        |  override def bar: String = "???"
        |  @targetName("bazExt")
        |  override def baz: Boolean = true
        |
        |object O:
        |  object C extends A:
        |    @targetName("testExtName")
        |    override def foo: Int = 3
        |""".stripMargin
    testQuickFixAllInFile(code, expected, hint)
  }

}
