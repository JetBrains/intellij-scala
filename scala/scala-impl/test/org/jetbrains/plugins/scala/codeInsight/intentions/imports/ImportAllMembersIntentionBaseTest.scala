package org.jetbrains.plugins.scala.codeInsight.intentions.imports

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

abstract class ImportAllMembersIntentionBaseTest extends ScalaIntentionTestBase {
  override def familyName: String = ScalaBundle.message("family.name.import.all.members")

  def testParameterizedDef(): Unit = {
    val text =
      """object A {
        |  scala.<caret>Option.empty[Int]
        |}""".stripMargin
    val result =
      """import scala.Option._
        |
        |object A {
        |  <caret>empty[Int]
        |}""".stripMargin
    doTest(text, result)
  }

  def testWithExistedImport(): Unit = {
    val text =
      """import math.E
        |
        |object A {
        |  m<caret>ath.Pi
        |}""".stripMargin
    val result =
      """import scala.math._
        |
        |object A {
        |  <caret>Pi
        |}""".stripMargin
    doTest(text, result)
  }

  def testLongRef1(): Unit = {
    val text =
      """object A {
        |  math.BigDecimal.RoundingMode.CEILING
        |  val x: math.BigDecimal.Ro<caret>undingMode.RoundingMode = null
        |}""".stripMargin
    val result =
      """import scala.math.BigDecimal.RoundingMode._
        |
        |object A {
        |  CEILING
        |  val x: <caret>RoundingMode = null
        |}""".stripMargin
    doTest(text, result)
  }

  def testLongRef2(): Unit = {
    val text =
      """object A {
        |  math.BigD<caret>ecimal.RoundingMode.CEILING
        |  val x: math.BigDecimal.RoundingMode.RoundingMode = null
        |}""".stripMargin
    val result =
      """import scala.math.BigDecimal._
        |
        |object A {
        |  <caret>RoundingMode.CEILING
        |  val x: RoundingMode.RoundingMode = null
        |}""".stripMargin
    doTest(text, result)
  }

  def testClasses(): Unit = {
    val text =
      """abstract class A extends ma<caret>th.Integral {
        |  val obj = math.BigDecimal
        |  val instance: math.BigDecimal = null
        |  val traitInst: math.Numeric = null
        |}
      """.stripMargin
    val result =
      """import scala.math._
        |
        |abstract class A extends <caret>Integral {
        |  val obj = BigDecimal
        |  val instance: BigDecimal = null
        |  val traitInst: Numeric = null
        |}
      """.stripMargin
    doTest(text, result)
  }

  def testAddToExistedImport(): Unit = {
    val text =
      """import scala.math.Pi
        |
        |object A {
        |  <caret>math.E + Pi
        |}""".stripMargin
    val result =
      """import scala.math._
        |
        |object A {
        |  <caret>E + Pi
        |}""".stripMargin
    doTest(text, result)
  }

  def testInfixExprWithNesting(): Unit = {
    val text =
      """object A {
        |  <caret>math floor math.Pi
        |}""".stripMargin
    val result =
      """import scala.math._
        |
        |object A {
        |  <caret>floor(Pi)
        |}""".stripMargin
    doTest(text, result)
  }

  def testPostfix(): Unit = {
    val text =
      """object A {
        |  println(<caret>math Pi)
        |}""".stripMargin
    val result =
      """import scala.math._
        |
        |object A {
        |  println(<caret>Pi)
        |}""".stripMargin
    doTest(text, result)
  }

  def testJavaStatic(): Unit = {
    val text =
      """object A {
        |  java.lang.<caret>Math.sin(java.lang.Math.PI)
        |}
        |""".stripMargin
    val result =
      """import java.lang.Math._
        |
        |object A {
        |  sin(PI)
        |}
        |""".stripMargin
    doTest(text, result)
  }

  def testNonStatic(): Unit = {
    val text =
      """object A {
        |  math.<caret>Pi.toString
        |}
      """.stripMargin
    checkIntentionIsNotAvailable(text)
  }

  def testInfixNonStatic(): Unit = {
    val text =
      """object A {
        |  math.<caret>Pi + 1
        |}
      """.stripMargin
    checkIntentionIsNotAvailable(text)
  }

  def testInImport(): Unit = {
    val text =
      """import m<caret>ath.E
        |
        |object A {
        |  val i: math.BigInt = null
        |  math.Pi
        |}""".stripMargin
    val result =
      """import scala.math._
        |
        |object A {
        |  val i: BigInt = null
        |  Pi
        |}""".stripMargin
    doTest(text, result)
  }

  def testImportConflicts(): Unit = {
    val text =
      """import java.lang.Math._
        |import scala.math.{abs, sin => sine}
        |
        |object A {
        |  val i: <caret>math.BigInt = null
        |  abs(sine(math.Pi + E))
        |}""".stripMargin
    val result =
      """import java.lang.Math._
        |import scala.math.{abs, sin => sine, E => _, _}
        |
        |object A {
        |  val i: BigInt = null
        |  abs(sine(Pi + E))
        |}""".stripMargin
    doTest(text, result)
  }

  def testImportConflictsNoMerge(): Unit = {
    val settings = ScalaCodeStyleSettings.getInstance(getProject)
    settings.setCollectImports(false)
    val text =
      """import java.lang.Math._
        |import scala.math.{abs, sin => sine}
        |
        |object A {
        |  val i: <caret>math.BigInt = null
        |  abs(sine(math.Pi + E))
        |}""".stripMargin
    val result =
      """import java.lang.Math._
        |import scala.math.abs
        |import scala.math.{sin => sine, E => _, _}
        |
        |object A {
        |  val i: BigInt = null
        |  abs(sine(Pi + E))
        |}""".stripMargin
    doTest(text, result)
    settings.setCollectImports(true)
  }
}
