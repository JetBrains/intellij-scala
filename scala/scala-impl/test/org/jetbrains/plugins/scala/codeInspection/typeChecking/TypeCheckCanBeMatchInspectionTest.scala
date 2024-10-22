package org.jetbrains.plugins.scala
package codeInspection.typeChecking

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

abstract class TypeCheckCanBeMatchInspectionTestBase extends ScalaInspectionTestBase {
  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[TypeCheckCanBeMatchInspection]

  override protected val description: String = TypeCheckCanBeMatchInspection.inspectionName

  private val hint: String = TypeCheckCanBeMatchInspection.inspectionName

  override protected def trimExpectedText: Boolean = false

  //noinspection JUnitMalformedDeclaration
  protected def testQuickFix(text: String, result: String): Unit = {
    testQuickFix(text, result, hint)
  }
}

class TypeCheckCanBeMatchInspectionTest extends TypeCheckCanBeMatchInspectionTestBase {

  def test_1(): Unit = {
    val selected =
      """
        |val x = 0
        |if (x.isInstanceOf[Int]) {
        |  x.toString
        |}""".stripMargin
    checkTextHasNoErrors(selected)
  }

  def test_2(): Unit = {
    val selected =
      s"""val x = 0
         |if (${START}x.isInstanceOf[Int]$END) {
         |  x.asInstanceOf[Int].toString
         |  println(x.asInstanceOf[Int])
         |}""".stripMargin
    checkTextHasError(selected)

    val text =
      s"""val x = 0
         |if (x.isInstanc${CARET}eOf[Int]) {
         |  x.asInstanceOf[Int].toString
         |  println(x.asInstanceOf[Int])
         |}""".stripMargin
    val result =
      """val x = 0
        |x match {
        |  case i: Int =>
        |    i.toString
        |    println(i)
        |  case _ =>
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_3(): Unit = {
    val selected =
      s"""val x = 0
         |if (${START}x.isInstanceOf[Int]$END) {
         |  val y = x.asInstanceOf[Int]
         |  x.asInstanceOf[Int].toString
         |  println(y)
         |}""".stripMargin
    checkTextHasError(selected)

    val text =
      s"""val x = 0
         |if (x.isInstanc${CARET}eOf[Int]) {
         |  val y = x.asInstanceOf[Int]
         |  x.asInstanceOf[Int].toString
         |  println(y)
         |}""".stripMargin
    val result =
      """val x = 0
        |x match {
        |  case y: Int =>
        |    y.toString
        |    println(y)
        |  case _ =>
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_4(): Unit = {
    val selected =
      s"""val x = 0
         |if (${START}x.isInstanceOf[Int]$END && x.asInstanceOf[Int] == 1) {
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |}""".stripMargin
    checkTextHasError(selected)

    val text =
      s"""val x = 0
         |if (${CARET}x.isInstanceOf[Int] && x.asInstanceOf[Int] == 1) {
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |}""".stripMargin
    val result =
      """val x = 0
        |x match {
        |  case y: Int if y == 1 =>
        |    println(y)
        |  case _ =>
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_5(): Unit = {
    val selected =
      s"""val x = 0
         |if (x > 0 && (${START}x.isInstanceOf[Int]$END && x.asInstanceOf[Int] == 1)) {
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |}""".stripMargin
    checkTextHasNoErrors(selected)
  }

  def test_6(): Unit = {
    val selected =
      s"""val x = 0
         |if (${START}x.isInstanceOf[Int]$END) {
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |} else if (x.isInstanceOf[Long]) {
         |  println(x)
         |} else println()""".stripMargin
    checkTextHasError(selected)

    val text =
      s"""val x = 0
         |if (${CARET}x.isInstanceOf[Int]) {
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |} else if (x.isInstanceOf[Long]) {
         |  println(x)
         |} else println()""".stripMargin
    val result =
      """val x = 0
        |x match {
        |  case y: Int =>
        |    println(y)
        |  case _: Long =>
        |    println(x)
        |  case _ => println()
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_7(): Unit = {
    val selected =
      s"""val x = 0
         |if (${START}x.isInstanceOf[Int]$END && x.asInstanceOf[Long] == 1) {
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |} else if (x.isInstanceOf[Long]) {
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |} else {
         |  println(x)
         |  println()
         |}""".stripMargin
    checkTextHasError(selected)

    val text =
      s"""val x = 0
         |if (${CARET}x.isInstanceOf[Int] && x.asInstanceOf[Long] == 1) {
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |} else if (x.isInstanceOf[Long]) {
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |} else {
         |  println(x)
         |  println()
         |}""".stripMargin
    val result =
      """val x = 0
        |x match {
        |  case y: Int if x.asInstanceOf[Long] == 1 =>
        |    println(y)
        |  case _: Long =>
        |    val y = x.asInstanceOf[Int]
        |    println(y)
        |  case _ =>
        |    println(x)
        |    println()
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_8a(): Unit = {
    val selected =
      s"""val x1 = 0
         |val x2 = 0
         |if (${START}x1.isInstanceOf[Int]$END && x2.isInstanceOf[Int]) {
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |} else if (x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |}""".stripMargin
    checkTextHasError(selected)

    val text =
      s"""val x1 = 0
         |val x2 = 0
         |if (x${CARET}1.isInstanceOf[Int] && x2.isInstanceOf[Int]) {
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |} else if (x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |}""".stripMargin
    val result =
      """val x1 = 0
        |val x2 = 0
        |x1 match {
        |  case y1: Int if x2.isInstanceOf[Int] =>
        |    val y2 = x2.asInstanceOf[Int]
        |    println(y1 + y2)
        |  case _: Long if x2.isInstanceOf[Long] =>
        |    val y1 = x1.asInstanceOf[Int]
        |    val y2 = x2.asInstanceOf[Int]
        |    println(y1 + y2)
        |  case _ =>
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_8b(): Unit = {
    val selected =
      s"""val x1 = 0
         |val x2 = 0
         |if (x1.isInstanceOf[Int] && x2.isI${CARET}nstanceOf[Int]) {
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |} else if (x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |}""".stripMargin
    checkTextHasNoErrors(selected)
  }

  def test_8c(): Unit = {
    val selected =
      s"""val x1 = 0
         |val x2 = 0
         |if (x1.isInstanceOf[Int] && x2.isInstanceOf[Int]) {
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |} else if (x1.isIn${CARET}stanceOf[Long] && x2.isInstanceOf[Long]) {
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |}""".stripMargin
    checkTextHasNoErrors(selected)
  }

  def test_9(): Unit = {
    val selected =
      s"""val x = 0
         |val i = 0
         |if (${START}x.isInstanceOf[Int]$END) {
         |  x.asInstanceOf[Int].toString
         |  println(x.asInstanceOf[Int])
         |}""".stripMargin
    checkTextHasError(selected)

    val text =
      s"""val x = 0
         |val i = 0
         |if (x.isInstanc${CARET}eOf[Int]) {
         |  x.asInstanceOf[Int].toString
         |  println(x.asInstanceOf[Int])
         |}""".stripMargin
    val result =
      """val x = 0
        |val i = 0
        |x match {
        |  case i1: Int =>
        |    i1.toString
        |    println(i1)
        |  case _ =>
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_10(): Unit = {
    val selected =
      s"""val x = 0
         |if (${START}x.isInstanceOf[Int]$END) x else if (x.isInstanceOf[Long]) x else 0""".stripMargin
    checkTextHasError(selected)

    val text =
      s"""val x = 0
         |if (x.isInstance${CARET}Of[Int]) x else if (x.isInstanceOf[Long]) x else 0""".stripMargin
    val result =
      """val x = 0
        |x match {
        |  case _: Int => x
        |  case _: Long => x
        |  case _ => 0
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_11(): Unit = {
    val selected =
      s"""import java.util
         |val foo = (p: AnyVal) => {
         |  if (${START}p.isInstanceOf[util.ArrayList[_]]$END) {}
         |  else if (p.isInstanceOf[scala.util.control.Breaks]) {}
         |}""".stripMargin
    checkTextHasError(selected)

    val text =
      """import java.util
        |val foo = (p: AnyVal) => {
        |  if (p.isInstanceOf[util.ArrayList[_]]) {}
        |  else if (p.isInstanceOf[scala.util.control.Breaks]) {}
        |}""".stripMargin
    val result =
      """import java.util
        |val foo = (p: AnyVal) => {
        |  p match {
        |    case _: util.ArrayList[_] =>
        |    case _: scala.util.control.Breaks =>
        |    case _ =>
        |  }
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_12(): Unit = {
    val selected =
      s"""def test2(p: AnyVal) {
         |  if (${START}p.isInstanceOf[T forSome {type T <: Number}]$END) {}
         |  else if (p.isInstanceOf[Int]) {}
         |}""".stripMargin
    checkTextHasError(selected)

    val text =
      """def test2(p: AnyVal) {
        |  if (p.isInstanceOf[T forSome {type T <: Number}]) {}
        |  else if (p.isInstanceOf[Int]) {}
        |}""".stripMargin
    val result =
      """def test2(p: AnyVal) {
        |  p match {
        |    case _: (T forSome {type T <: Number}) =>
        |    case _: Int =>
        |    case _ =>
        |  }
        |}""".stripMargin
    testQuickFix(text, result)
  }

}

class TypeCheckCanBeMatchInspectionTest_Scala3 extends TypeCheckCanBeMatchInspectionTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3_0

  override protected def createTestText(text: String) = text

  def test_1(): Unit = {
    val selected =
      """
        |val x = 0
        |if x.isInstanceOf[Int] then
        |  x.toString
        |""".stripMargin
    checkTextHasNoErrors(selected)
  }

  def test_2(): Unit = {
    val selected =
      s"""val x = 0
         |if ${START}x.isInstanceOf[Int]$END then
         |  x.asInstanceOf[Int].toString
         |  println(x.asInstanceOf[Int])
         |""".stripMargin
    checkTextHasError(selected)

    val text =
      s"""val x = 0
         |if x.isInstanc${CARET}eOf[Int] then
         |  x.asInstanceOf[Int].toString
         |  println(x.asInstanceOf[Int])
         |""".stripMargin
    val result =
      """val x = 0
        |x match
        |  case i: Int =>
        |    i.toString
        |    println(i)
        |  case _ =>
        |""".stripMargin
    testQuickFix(text, result)
  }

  def test_3(): Unit = {
    val selected =
      s"""val x = 0
         |if ${START}x.isInstanceOf[Int]$END then
         |  val y = x.asInstanceOf[Int]
         |  x.asInstanceOf[Int].toString
         |  println(y)
         |""".stripMargin
    checkTextHasError(selected)

    val text =
      s"""val x = 0
         |if x.isInstanc${CARET}eOf[Int] then
         |  val y = x.asInstanceOf[Int]
         |  x.asInstanceOf[Int].toString
         |  println(y)
         |""".stripMargin
    val result =
      """val x = 0
        |x match
        |  case y: Int =>
        |    y.toString
        |    println(y)
        |  case _ =>
        |""".stripMargin
    testQuickFix(text, result)
  }

  def test_4(): Unit = {
    val selected =
      s"""val x = 0
         |if ${START}x.isInstanceOf[Int]$END && x.asInstanceOf[Int] == 1 then
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |""".stripMargin
    checkTextHasError(selected)

    val text =
      s"""val x = 0
         |if ${CARET}x.isInstanceOf[Int] && x.asInstanceOf[Int] == 1 then
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |""".stripMargin
    val result =
      """val x = 0
        |x match
        |  case y: Int if y == 1 =>
        |    println(y)
        |  case _ =>
        |""".stripMargin
    testQuickFix(text, result)
  }

  def test_5(): Unit = {
    val selected =
      s"""val x = 0
         |if x > 0 && (${START}x.isInstanceOf[Int]$END && x.asInstanceOf[Int] == 1) then
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |""".stripMargin
    checkTextHasNoErrors(selected)
  }

  def test_6(): Unit = {
    val selected =
      s"""val x = 0
         |if ${START}x.isInstanceOf[Int]$END then
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |else if (x.isInstanceOf[Long] then
         |  println(x)
         |else println()""".stripMargin
    checkTextHasError(selected)

    val text =
      s"""val x = 0
         |if ${CARET}x.isInstanceOf[Int] then
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |else if (x.isInstanceOf[Long] then
         |  println(x)
         |else println()""".stripMargin
    val result =
      """val x = 0
        |x match
        |  case y: Int =>
        |    println(y)
        |  case _: Long => println(x)
        |  case _ => println()
        |""".stripMargin
    testQuickFix(text, result)
  }

  def test_7(): Unit = {
    val selected =
      s"""val x = 0
         |if ${START}x.isInstanceOf[Int]$END && x.asInstanceOf[Long] == 1 then
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |else if x.isInstanceOf[Long] then
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |else
         |  println(x)
         |  println()
         |""".stripMargin
    checkTextHasError(selected)

    val text =
      s"""val x = 0
         |if ${CARET}x.isInstanceOf[Int] && x.asInstanceOf[Long] == 1 then
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |else if x.isInstanceOf[Long] then
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |else
         |  println(x)
         |  println()
         |""".stripMargin
    val result =
      """val x = 0
        |x match
        |  case y: Int if x.asInstanceOf[Long] == 1 =>
        |    println(y)
        |  case _: Long =>
        |    val y = x.asInstanceOf[Int]
        |    println(y)
        |  case _ =>
        |    println(x)
        |    println()
        |""".stripMargin
    testQuickFix(text, result)
  }

  def test_8a(): Unit = {
    val selected =
      s"""val x1 = 0
         |val x2 = 0
         |if ${START}x1.isInstanceOf[Int]$END && x2.isInstanceOf[Int] then
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |else if x1.isInstanceOf[Long] && x2.isInstanceOf[Long] then
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |""".stripMargin
    checkTextHasError(selected)

    val text =
      s"""val x1 = 0
         |val x2 = 0
         |if x${CARET}1.isInstanceOf[Int] && x2.isInstanceOf[Int] then
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |else if (x1.isInstanceOf[Long] && x2.isInstanceOf[Long] then
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |""".stripMargin
    val result =
      """val x1 = 0
        |val x2 = 0
        |x1 match
        |  case y1: Int if x2.isInstanceOf[Int] =>
        |    val y2 = x2.asInstanceOf[Int]
        |    println(y1 + y2)
        |  case _: Long if x2.isInstanceOf[Long] =>
        |    val y1 = x1.asInstanceOf[Int]
        |    val y2 = x2.asInstanceOf[Int]
        |    println(y1 + y2)
        |  case _ =>
        |""".stripMargin
    testQuickFix(text, result)
  }

  def test_8b(): Unit = {
    val selected =
      s"""val x1 = 0
         |val x2 = 0
         |if x1.isInstanceOf[Int] && x2.isI${CARET}nstanceOf[Int] then
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |else if x1.isInstanceOf[Long] && x2.isInstanceOf[Long] then
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |""".stripMargin
    checkTextHasNoErrors(selected)
  }

  def test_8c(): Unit = {
    val selected =
      s"""val x1 = 0
         |val x2 = 0
         |if x1.isInstanceOf[Int] && x2.isInstanceOf[Int] then
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |else if x1.isIn${CARET}stanceOf[Long] && x2.isInstanceOf[Long] then
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |""".stripMargin
    checkTextHasNoErrors(selected)
  }

  def test_9(): Unit = {
    val selected =
      s"""val x = 0
         |val i = 0
         |if ${START}x.isInstanceOf[Int]$END then
         |  x.asInstanceOf[Int].toString
         |  println(x.asInstanceOf[Int])
         |""".stripMargin
    checkTextHasError(selected)

    val text =
      s"""val x = 0
         |val i = 0
         |if x.isInstanc${CARET}eOf[Int] then
         |  x.asInstanceOf[Int].toString
         |  println(x.asInstanceOf[Int])
         |""".stripMargin
    val result =
      """val x = 0
        |val i = 0
        |x match
        |  case i1: Int =>
        |    i1.toString
        |    println(i1)
        |  case _ =>
        |""".stripMargin
    testQuickFix(text, result)
  }

  def test_10(): Unit = {
    val selected =
      s"""val x = 0
         |if ${START}x.isInstanceOf[Int]$END then x else if x.isInstanceOf[Long] then x else 0""".stripMargin
    checkTextHasError(selected)

    val text =
      s"""val x = 0
         |if x.isInstance${CARET}Of[Int] then x else if x.isInstanceOf[Long] then x else 0""".stripMargin
    val result =
      """val x = 0
        |x match
        |  case _: Int => x
        |  case _: Long => x
        |  case _ => 0
        |""".stripMargin
    testQuickFix(text, result)
  }

  def test_11(): Unit = {
    val selected =
      s"""import java.util
         |val foo = (p: AnyVal) => {
         |  if (${START}p.isInstanceOf[util.ArrayList[_]]$END) {}
         |  else if (p.isInstanceOf[scala.util.control.Breaks]) {}
         |}""".stripMargin
    checkTextHasError(selected)

    val text =
      """import java.util
        |val foo = (p: AnyVal) => {
        |  if (p.isInstanceOf[util.ArrayList[_]]) {}
        |  else if (p.isInstanceOf[scala.util.control.Breaks]) {}
        |}""".stripMargin
    val result =
      """import java.util
        |val foo = (p: AnyVal) => {
        |  p match
        |    case _: util.ArrayList[_] =>
        |    case _: scala.util.control.Breaks =>
        |    case _ =>
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_12(): Unit = {
    val selected =
      s"""def test2(p: AnyVal) =
         |  if ${START}p.isInstanceOf[T forSome {type T <: Number}]$END then {}
         |  else if p.isInstanceOf[Int] then {}
         |""".stripMargin
    checkTextHasError(selected)

    val text =
      """def test2(p: AnyVal) =
        |  if p.isInstanceOf[T forSome {type T <: Number}] then {}
        |  else if p.isInstanceOf[Int] then {}
        |""".stripMargin
    val result =
      """def test2(p: AnyVal) =
        |  p match
        |    case _: (T forSome {type T <: Number}) =>
        |    case _: Int =>
        |    case _ =>
        |""".stripMargin
    testQuickFix(text, result)
  }
}
