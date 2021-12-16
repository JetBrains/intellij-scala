package org.jetbrains.plugins.scala.codeInspection.targetNameAnnotation

import com.intellij.java.analysis.JavaAnalysisBundle
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class OverridingAddingTargetNameInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection = classOf[OverridingAddingTargetNameInspection]
  override protected val description = OverridingAddingTargetNameInspection.message

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  private val hint = JavaAnalysisBundle.message("remove.annotation")

  def testSingle(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class A:
         |  def foo: Int = 1
         |
         |class B extends A:
         |  @targetName("overriddenFoo")
         |  override def ${START}foo$END: Int = 2
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |class A:
        |  def foo: Int = 1
        |
        |class B extends A:
        |  override def foo: Int = 2
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testFixAll(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |trait A:
         |  def foo: Int = 1
         |  @targetName("baz")
         |  def bar: String = "..."
         |
         |class B extends A:
         |  def boo: Boolean = true
         |
         |class C extends B:
         |  @targetName("overriddenFoo")
         |  override def ${START}foo$END: Int = 2
         |  @targetName("baz")
         |  override def bar: String = "???"
         |  @targetName("overriddenBoo")
         |  override def ${START}boo$END: Boolean = false
         |
         |object O:
         |  object D extends B:
         |    @targetName("doo")
         |    override def ${START}boo$END: Boolean = false
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |trait A:
        |  def foo: Int = 1
        |  @targetName("baz")
        |  def bar: String = "..."
        |
        |class B extends A:
        |  def boo: Boolean = true
        |
        |class C extends B:
        |  override def foo: Int = 2
        |  @targetName("baz")
        |  override def bar: String = "???"
        |  override def boo: Boolean = false
        |
        |object O:
        |  object D extends B:
        |    override def boo: Boolean = false
        |""".stripMargin
    testQuickFixAllInFile(code, expected, hint)
  }

}
