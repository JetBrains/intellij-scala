package org.jetbrains.plugins.scala.codeInsight.intentions.imports

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

abstract class ImportStableMemberIntentionBaseTest extends ScalaIntentionTestBase {
  override def familyName: String = ScalaBundle.message("family.name.import.member.with.stable.path")

  def testParameterizedDef(): Unit = {
    val text =
      """object A {
        |  scala.Option.<caret>empty[Int]
        |}""".stripMargin
    val result =
      """import scala.Option.empty
        |
        |object A {
        |  <caret>empty[Int]
        |}""".stripMargin
    doTest(text, result)
  }

  def testVal(): Unit = {
    val text =
      """object A {
        |  math.P<caret>i
        |}""".stripMargin
    val result =
      """import scala.math.Pi
        |
        |object A {
        |  P<caret>i
        |}""".stripMargin
    doTest(text, result)
  }

  def testTypeAlias(): Unit = {
    val text =
      """object A {
        |  val x: math.BigDecimal.RoundingMode.R<caret>oundingMode = null
        |}""".stripMargin
    val result =
      """import scala.math.BigDecimal.RoundingMode.RoundingMode
        |
        |object A {
        |  val x: R<caret>oundingMode = null
        |}""".stripMargin
    doTest(text, result)
  }

  def testObject(): Unit = {
    val text =
      """object A {
        |  BigDecimal.<caret>RoundingMode
        |}""".stripMargin
    val result =
      """import scala.math.BigDecimal.RoundingMode
        |
        |object A {
        |  <caret>RoundingMode
        |}""".stripMargin
    doTest(text, result)
  }

  def testMultiple(): Unit = {
    val text =
      """object A {
        |  math.<caret>Pi * math.Pi
        |}""".stripMargin
    val result =
      """import scala.math.Pi
        |
        |object A {
        |  Pi * Pi
        |}""".stripMargin
    doTest(text, result)
  }

  def testAddToExistedImport(): Unit = {
    val text =
      """import scala.math.Pi
        |
        |object A {
        |  math.<caret>E
        |}""".stripMargin
    val result =
      """import scala.math.{E, Pi}
        |
        |object A {
        |  E
        |}""".stripMargin
    doTest(text, result)
  }

  def testInfixExpr(): Unit = {
    val text =
      """object A {
        |  math <caret>floor math.Pi
        |}""".stripMargin
    val result =
      """import scala.math.floor
        |
        |object A {
        |  <caret>floor(math.Pi)
        |}""".stripMargin
    doTest(text, result)
  }

  def testNesting(): Unit = {
    val text =
      """
        |object A {
        |  math <caret>floor math.floor(math Pi)
        |}""".stripMargin
    val result =
      """import scala.math.floor
        |
        |object A {
        |  floor(floor(math Pi))
        |}""".stripMargin
    doTest(text, result)
  }

  def testPostfix(): Unit = {
    val text =
      """object A {
        |  println(math <caret>Pi)
        |}""".stripMargin
    val result =
      """import scala.math.Pi
        |
        |object A {
        |  println(<caret>Pi)
        |}""".stripMargin
    doTest(text, result)
  }

  def testJavaStaticField(): Unit = {
    val text =
      """object A {
        |  java.util.GregorianCalendar.<caret>BC
        |}""".stripMargin
    val result =
      """import java.util.GregorianCalendar.BC
        |
        |object A {
        |  <caret>BC
        |}""".stripMargin
    doTest(text, result)
  }

  def testJavaStaticMethod(): Unit = {
    val text =
      """object A {
        |  java.util.Arrays.<caret>fill(new Array[Int](1), 1)
        |}""".stripMargin
    val result =
      """import java.util.Arrays.fill
        |
        |object A {
        |  <caret>fill(new Array[Int](1), 1)
        |}""".stripMargin
    doTest(text, result)
  }

  def testNonStatic(): Unit = {
    val text =
      """object A {
        |  math.Pi.<caret>toString
        |}
      """.stripMargin
    checkIntentionIsNotAvailable(text)
  }

  def testInfixNonStatic(): Unit = {
    val text =
      """object A {
        |  math.Pi <caret>+ 1
        |}
      """.stripMargin
    checkIntentionIsNotAvailable(text)
  }

  def testInImport(): Unit = {
    val text =
      """import math.<caret>E
        |
        |object A {
        |  math.Pi
        |}
      """.stripMargin
    checkIntentionIsNotAvailable(text)
  }

}
