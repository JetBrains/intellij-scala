package org.jetbrains.plugins.scala.codeInspection.targetNameAnnotation

import com.intellij.java.analysis.JavaAnalysisBundle
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class MultipleTargetNameAnnotationsInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection = classOf[MultipleTargetNameAnnotationsInspection]
  override protected val description = MultipleTargetNameAnnotationsInspection.message

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  private val hint = JavaAnalysisBundle.message("remove.annotation")

  def testMultipleAnnotations(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class A(val value: Int):
         |  $START@targetName("first")$END
         |  $START@targetName("second")$END
         |  $START@targetName("third")$END
         |  def *(that: A): A = this.value * that.value
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |class A(val value: Int):
        |  @targetName("second")
        |  @targetName("third")
        |  def *(that: A): A = this.value * that.value
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testSingleAnnotation(): Unit = {
    val code =
      """import scala.annotation.targetName
        |
        |class A(val value: Int):
        |  @targetName("multiply")
        |  def *(that: A): A = this.value * that.value
        |""".stripMargin
    checkTextHasNoErrors(code)
  }

  def testNoAnnotations(): Unit = {
    val code =
      """class A(val value: Int):
        |  def *(that: A): A = this.value * that.value
        |""".stripMargin
    checkTextHasNoErrors(code)
  }
}
