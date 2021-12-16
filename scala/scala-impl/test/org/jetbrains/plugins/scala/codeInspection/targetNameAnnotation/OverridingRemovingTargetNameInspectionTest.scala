package org.jetbrains.plugins.scala.codeInspection.targetNameAnnotation

import org.jetbrains.plugins.scala.codeInspection.{ScalaInspectionBundle, ScalaInspectionTestBase}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class OverridingRemovingTargetNameInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection = classOf[OverridingRemovingTargetNameInspection]
  override protected val description = OverridingRemovingTargetNameInspection.message("testExtName")

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  private val hint = ScalaInspectionBundle.message("add.targetname.annotation")

  def testSingle(): Unit = {
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
