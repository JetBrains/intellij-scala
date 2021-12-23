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

  def testEnumCase(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |enum E:
         |  case A
         |  $START@targetName("first")$END
         |  $START@targetName("second")$END
         |  $START@targetName("third")$END
         |  case B
         |  case C
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |enum E:
        |  case A
        |  @targetName("second")
        |  @targetName("third")
        |  case B
        |  case C
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testEnumCaseWithConstructor(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |enum E:
         |  case A
         |  $START@targetName("first")$END
         |  $START@targetName("second")$END
         |  $START@targetName("third")$END
         |  case B(i: Int)
         |  case C
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |enum E:
        |  case A
        |  @targetName("second")
        |  @targetName("third")
        |  case B(i: Int)
        |  case C
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testEnum(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |$START@targetName("first")$END
         |$START@targetName("second")$END
         |$START@targetName("third")$END
         |enum E:
         |  case A, B
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |@targetName("second")
        |@targetName("third")
        |enum E:
        |  case A, B
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testTrait(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |$START@targetName("first")$END
         |$START@targetName("second")$END
         |$START@targetName("third")$END
         |trait T
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |@targetName("second")
        |@targetName("third")
        |trait T
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testObject(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |$START@targetName("first")$END
         |$START@targetName("second")$END
         |$START@targetName("third")$END
         |object O
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |@targetName("second")
        |@targetName("third")
        |object O
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testClass(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |$START@targetName("first")$END
         |$START@targetName("second")$END
         |$START@targetName("third")$END
         |class C
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |@targetName("second")
        |@targetName("third")
        |class C
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testValDecl(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class C:
         |  $START@targetName("first")$END
         |  $START@targetName("second")$END
         |  $START@targetName("third")$END
         |  val v: Int
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |class C:
        |  @targetName("second")
        |  @targetName("third")
        |  val v: Int
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testValDef(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class C:
         |  $START@targetName("first")$END
         |  $START@targetName("second")$END
         |  $START@targetName("third")$END
         |  val v: Int = 42
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |class C:
        |  @targetName("second")
        |  @targetName("third")
        |  val v: Int = 42
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testVarDecl(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class C:
         |  $START@targetName("first")$END
         |  $START@targetName("second")$END
         |  $START@targetName("third")$END
         |  var v: Int
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |class C:
        |  @targetName("second")
        |  @targetName("third")
        |  var v: Int
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testVarDef(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class C:
         |  $START@targetName("first")$END
         |  $START@targetName("second")$END
         |  $START@targetName("third")$END
         |  var v: Int = 42
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |class C:
        |  @targetName("second")
        |  @targetName("third")
        |  var v: Int = 42
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testDefDecl(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class C:
         |  $START@targetName("first")$END
         |  $START@targetName("second")$END
         |  $START@targetName("third")$END
         |  def f(i: Int): Int
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |class C:
        |  @targetName("second")
        |  @targetName("third")
        |  def f(i: Int): Int
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testDefDef(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class C:
         |  $START@targetName("first")$END
         |  $START@targetName("second")$END
         |  $START@targetName("third")$END
         |  def f(i: Int): Int = i * 2
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |class C:
        |  @targetName("second")
        |  @targetName("third")
        |  def f(i: Int): Int = i * 2
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testGiven(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |$START@targetName("first")$END
         |$START@targetName("second")$END
         |$START@targetName("third")$END
         |given foo(using i: Int): String = " " * i
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |@targetName("second")
        |@targetName("third")
        |given foo(using i: Int): String = " " * i
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testTypeAliasDecl(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |trait T:
         |  $START@targetName("first")$END
         |  $START@targetName("second")$END
         |  $START@targetName("third")$END
         |  type TT
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |trait T:
        |  @targetName("second")
        |  @targetName("third")
        |  type TT
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testTypeAliasDef(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |trait T:
         |  $START@targetName("first")$END
         |  $START@targetName("second")$END
         |  $START@targetName("third")$END
         |  type TT = Long
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |trait T:
        |  @targetName("second")
        |  @targetName("third")
        |  type TT = Long
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testClassParam(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class C(i: Int, $START@targetName("first")$END $START@targetName("second")$END $START@targetName("third")$END s: String)
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
         |
         |class C(i: Int, @targetName("second") @targetName("third") s: String)
         |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testTraitParam(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |trait T(i: Int, $START@targetName("first")$END $START@targetName("second")$END $START@targetName("third")$END s: String)
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |trait T(i: Int, @targetName("second") @targetName("third") s: String)
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testEnumParam(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |enum E(i: Int, $START@targetName("first")$END $START@targetName("second")$END $START@targetName("third")$END s: String):
         |  case A extends E(42, "foo")
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |enum E(i: Int, @targetName("second") @targetName("third") s: String):
        |  case A extends E(42, "foo")
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testEnumCaseParam(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |enum E:
         |  case A(i: Int, $START@targetName("first")$END $START@targetName("second")$END $START@targetName("third")$END s: String)
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |enum E:
        |  case A(i: Int, @targetName("second") @targetName("third") s: String)
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testDefParam(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |def foo(i: Int, $START@targetName("first")$END $START@targetName("second")$END $START@targetName("third")$END s: String): Int
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |def foo(i: Int, @targetName("second") @targetName("third") s: String): Int
        |""".stripMargin
    testQuickFix(code, expected, hint)
  }

  def testGivenParam(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |given foo($START@targetName("first")$END $START@targetName("second")$END $START@targetName("third")$END s: String): Int
         |""".stripMargin
    checkTextHasError(code)

    val expected =
      """import scala.annotation.targetName
        |
        |given foo(@targetName("second") @targetName("third") s: String): Int
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
