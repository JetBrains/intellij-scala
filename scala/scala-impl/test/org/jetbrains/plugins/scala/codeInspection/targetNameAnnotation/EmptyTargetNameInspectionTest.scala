package org.jetbrains.plugins.scala.codeInspection.targetNameAnnotation

import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class EmptyTargetNameInspectionTest extends ScalaInspectionTestBase {
  override protected val classOfInspection = classOf[EmptyTargetNameInspection]
  override protected val description = EmptyTargetNameInspection.message

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  def testEnumCase(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |enum E:
         |  case A
         |  @targetName($START""$END)
         |  case B
         |  case C
         |""".stripMargin
    checkTextHasError(code)
  }

  def testEnumCaseWithConstructor(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |enum E:
         |  case A
         |  @targetName($START""$END)
         |  case B(i: Int)
         |  case C
         |""".stripMargin
    checkTextHasError(code)
  }

  def testEnum(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |@targetName($START""$END)
         |enum E:
         |  case A, B
         |""".stripMargin
    checkTextHasError(code)
  }

  def testTrait(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |@targetName($START""$END)
         |trait T
         |""".stripMargin
    checkTextHasError(code)
  }

  def testObject(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |@targetName($START""$END)
         |object O
         |""".stripMargin
    checkTextHasError(code)
  }

  def testClass(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |@targetName($START""$END)
         |class C
         |""".stripMargin
    checkTextHasError(code)
  }

  def testValDecl(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class C:
         |  @targetName($START""$END)
         |  val v: Int
         |""".stripMargin
    checkTextHasError(code)
  }

  def testValDef(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class C:
         |  @targetName($START""$END)
         |  val v: Int = 42
         |""".stripMargin
    checkTextHasError(code)
  }

  def testVarDecl(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class C:
         |  @targetName($START""$END)
         |  var v: Int
         |""".stripMargin
    checkTextHasError(code)
  }

  def testVarDef(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class C:
         |  @targetName($START""$END)
         |  var v: Int = 42
         |""".stripMargin
    checkTextHasError(code)
  }

  def testDefDecl(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class C:
         |  @targetName($START""$END)
         |  def f(i: Int): Int
         |""".stripMargin
    checkTextHasError(code)
  }

  def testDefDef(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class C:
         |  @targetName($START""$END)
         |  def f(i: Int): Int = i * 2
         |""".stripMargin
    checkTextHasError(code)
  }

  def testGiven(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |@targetName($START""$END)
         |given foo(using i: Int): String = " " * i
         |""".stripMargin
    checkTextHasError(code)
  }

  def testTypeAliasDecl(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |trait T:
         |  @targetName($START""$END)
         |  type TT
         |""".stripMargin
    checkTextHasError(code)
  }

  def testTypeAliasDef(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |trait T:
         |  @targetName($START""$END)
         |  type TT = Long
         |""".stripMargin
    checkTextHasError(code)
  }

  def testClassParam(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |class C(i: Int, @targetName($START""$END) s: String)
         |""".stripMargin
    checkTextHasError(code)
  }

  def testTraitParam(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |trait T(i: Int, @targetName($START""$END) s: String)
         |""".stripMargin
    checkTextHasError(code)
  }

  def testEnumParam(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |enum E(i: Int, @targetName($START""$END) s: String):
         |  case A extends E(42, "foo")
         |""".stripMargin
    checkTextHasError(code)
  }

  def testEnumCaseParam(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |enum E:
         |  case A(i: Int, @targetName($START""$END) s: String)
         |""".stripMargin
    checkTextHasError(code)
  }

  def testDefParam(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |def foo(i: Int, @targetName($START""$END) s: String): Int
         |""".stripMargin
    checkTextHasError(code)
  }

  def testGivenParam(): Unit = {
    val code =
      s"""import scala.annotation.targetName
         |
         |given foo(@targetName($START""$END) s: String): Int
         |""".stripMargin
    checkTextHasError(code)
  }

}
