package org.jetbrains.plugins.scala.codeInspection.targetNameAnnotation

import com.intellij.java.analysis.JavaAnalysisBundle
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class MultipleTargetsTargetNameInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection = classOf[MultipleTargetsTargetNameInspection]
  override protected val description = MultipleTargetsTargetNameInspection.message

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  private val hint = JavaAnalysisBundle.message("remove.annotation")

  def testPatternDefinition(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class A(values: List[Int]):
         |  $START@targetName("head_and_tail")$END
         |  val (head, tail) = (values.head, values.tail)
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |class A(values: List[Int]):
        |  val (head, tail) = (values.head, values.tail)
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testEnumCases(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |enum E:
         |  case A
         |  $START@targetName("b_and_c")$END
         |  case B, C
         |  case D
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |enum E:
        |  case A
        |  case B, C
        |  case D
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testAllInFile(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class A(val value: Int):
         |  $START@targetName("double_and_square")$END
         |  val (double, square) = (value * 2, value * value)
         |
         |  @targetName("multiply")
         |  def *(that: A): A = this.value * that.value
         |
         |enum E:
         |  @targetName("firstCase")
         |  case A
         |  $START@targetName("b_and_c")$END
         |  case B, C
         |  case D
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |class A(val value: Int):
        |  val (double, square) = (value * 2, value * value)
        |
        |  @targetName("multiply")
        |  def *(that: A): A = this.value * that.value
        |
        |enum E:
        |  @targetName("firstCase")
        |  case A
        |  case B, C
        |  case D
        |""".stripMargin
    testQuickFixAllInFile(code, expected, hint)
  }
}
