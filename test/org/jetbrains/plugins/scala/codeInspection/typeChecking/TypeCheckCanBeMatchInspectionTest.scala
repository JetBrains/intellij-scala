package org.jetbrains.plugins.scala
package codeInspection.typeChecking

import com.intellij.codeInspection.LocalInspectionTool
import org.jetbrains.plugins.scala.codeInspection.ScalaLightInspectionFixtureTestAdapter

/**
 * Nikolay.Tropin
 * 5/15/13
 */
class TypeCheckCanBeMatchInspectionTest extends ScalaLightInspectionFixtureTestAdapter{

  val annotation = TypeCheckCanBeMatchInspection.inspectionName
  val hint = TypeCheckCanBeMatchInspection.inspectionName

  protected def classOfInspection: Class[_ <: LocalInspectionTool] = classOf[TypeCheckCanBeMatchInspection]

  private def testQuickFix(text: String, result: String) {
    testFix(text, result, hint)
  }

  def test_1() {
    val selected = """
                     |val x = 0
                     |if (x.isInstanceOf[Int]) {
                     |  x.toString
                     |}"""
    checkTextHasNoErrors(selected)
  }

  def test_2() {
    val selected = s"""val x = 0
                     |if (${START}x.isInstanceOf[Int]$END) {
                     |  x.asInstanceOf[Int].toString
                     |  println(x.asInstanceOf[Int])
                     |}"""
    check(selected)

    val text = """val x = 0
                     |if (x.isInstanc<caret>eOf[Int]) {
                     |  x.asInstanceOf[Int].toString
                     |  println(x.asInstanceOf[Int])
                     |}"""
    val result = """val x = 0
                   |x match {
                   |  case i: Int =>
                   |    i.toString
                   |    println(i)
                   |  case _ =>
                   |}"""
    testQuickFix(text, result)
  }

  def test_3() {
    val selected = s"""val x = 0
                     |if (${START}x.isInstanceOf[Int]$END) {
                     |  val y = x.asInstanceOf[Int]
                     |  x.asInstanceOf[Int].toString
                     |  println(y)
                     |}"""
    check(selected)

    val text = """val x = 0
                 |if (x.isInstanc<caret>eOf[Int]) {
                 |  val y = x.asInstanceOf[Int]
                 |  x.asInstanceOf[Int].toString
                 |  println(y)
                 |}"""
    val result = """val x = 0
                   |x match {
                   |  case y: Int =>
                   |    y.toString
                   |    println(y)
                   |  case _ =>
                   |}"""
    testQuickFix(text, result)
  }

  def test_4() {
    val selected = s"""val x = 0
                     |if (${START}x.isInstanceOf[Int]$END && x.asInstanceOf[Int] == 1) {
                     |  val y = x.asInstanceOf[Int]
                     |  println(y)
                     |}"""
    check(selected)

    val text = """val x = 0
                 |if (<caret>x.isInstanceOf[Int] && x.asInstanceOf[Int] == 1) {
                 |  val y = x.asInstanceOf[Int]
                 |  println(y)
                 |}"""
    val result = """val x = 0
                   |x match {
                   |  case y: Int if y == 1 =>
                   |    println(y)
                   |  case _ =>
                   |}"""
    testQuickFix(text, result)
  }

  def test_5() {
    val selected = s"""val x = 0
                     |if (x > 0 && (${START}x.isInstanceOf[Int]$END && x.asInstanceOf[Int] == 1)) {
                     |  val y = x.asInstanceOf[Int]
                     |  println(y)
                     |}"""
    checkTextHasNoErrors(selected)
  }

  def test_6() {
    val selected = s"""val x = 0
                     |if (${START}x.isInstanceOf[Int]$END) {
                     |  val y = x.asInstanceOf[Int]
                     |  println(y)
                     |} else if (x.isInstanceOf[Long]) {
                     |  println(x)
                     |} else println()"""
    check(selected)

    val text = """val x = 0
                 |if (<caret>x.isInstanceOf[Int]) {
                 |  val y = x.asInstanceOf[Int]
                 |  println(y)
                 |} else if (x.isInstanceOf[Long]) {
                 |  println(x)
                 |} else println()"""
    val result = """val x = 0
                   |x match {
                   |  case y: Int =>
                   |    println(y)
                   |  case _: Long =>
                   |    println(x)
                   |  case _ => println()
                   |}"""
    testQuickFix(text, result)
  }

  def test_7() {
    val selected = s"""val x = 0
                     |if (${START}x.isInstanceOf[Int]$END && x.asInstanceOf[Long] == 1) {
                     |  val y = x.asInstanceOf[Int]
                     |  println(y)
                     |} else if (x.isInstanceOf[Long]) {
                     |  val y = x.asInstanceOf[Int]
                     |  println(y)
                     |} else {
                     |  println(x)
                     |  println()
                     |}"""
    check(selected)

    val text = """val x = 0
                 |if (<caret>x.isInstanceOf[Int] && x.asInstanceOf[Long] == 1) {
                 |  val y = x.asInstanceOf[Int]
                 |  println(y)
                 |} else if (x.isInstanceOf[Long]) {
                 |  val y = x.asInstanceOf[Int]
                 |  println(y)
                 |} else {
                 |  println(x)
                 |  println()
                 |}"""
    val result = """val x = 0
                   |x match {
                   |  case y: Int if x.asInstanceOf[Long] == 1 =>
                   |    println(y)
                   |  case _: Long =>
                   |    val y = x.asInstanceOf[Int]
                   |    println(y)
                   |  case _ =>
                   |    println(x)
                   |    println()
                   |}"""
    testQuickFix(text, result)
  }

  def test_8a() {
    val selected = s"""val x1 = 0
                     |val x2 = 0
                     |if (${START}x1.isInstanceOf[Int]$END && x2.isInstanceOf[Int]) {
                     |  val y1 = x1.asInstanceOf[Int]
                     |  val y2 = x2.asInstanceOf[Int]
                     |  println(y1 + y2)
                     |} else if (x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
                     |  val y1 = x1.asInstanceOf[Int]
                     |  val y2 = x2.asInstanceOf[Int]
                     |  println(y1 + y2)
                     |}"""
    check(selected)

    val text = """val x1 = 0
                 |val x2 = 0
                 |if (x<caret>1.isInstanceOf[Int] && x2.isInstanceOf[Int]) {
                 |  val y1 = x1.asInstanceOf[Int]
                 |  val y2 = x2.asInstanceOf[Int]
                 |  println(y1 + y2)
                 |} else if (x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
                 |  val y1 = x1.asInstanceOf[Int]
                 |  val y2 = x2.asInstanceOf[Int]
                 |  println(y1 + y2)
                 |}"""
    val result = """val x1 = 0
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
                   |}"""
    testQuickFix(text, result)
  }

  def test_8b() {
    val selected = s"""val x1 = 0
                     |val x2 = 0
                     |if (x1.isInstanceOf[Int] && x2.isI${CARET_MARKER}nstanceOf[Int]) {
                     |  val y1 = x1.asInstanceOf[Int]
                     |  val y2 = x2.asInstanceOf[Int]
                     |  println(y1 + y2)
                     |} else if (x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
                     |  val y1 = x1.asInstanceOf[Int]
                     |  val y2 = x2.asInstanceOf[Int]
                     |  println(y1 + y2)
                     |}"""
    checkTextHasNoErrors(selected)
  }

  def test_8c() {
    val selected = s"""val x1 = 0
                     |val x2 = 0
                     |if (x1.isInstanceOf[Int] && x2.isInstanceOf[Int]) {
                     |  val y1 = x1.asInstanceOf[Int]
                     |  val y2 = x2.asInstanceOf[Int]
                     |  println(y1 + y2)
                     |} else if (x1.isIn${CARET_MARKER}stanceOf[Long] && x2.isInstanceOf[Long]) {
                     |  val y1 = x1.asInstanceOf[Int]
                     |  val y2 = x2.asInstanceOf[Int]
                     |  println(y1 + y2)
                     |}"""
    checkTextHasNoErrors(selected)
  }

  def test_9() {
    val selected = s"""val x = 0
                     |val i = 0
                     |if (${START}x.isInstanceOf[Int]$END) {
                     |  x.asInstanceOf[Int].toString
                     |  println(x.asInstanceOf[Int])
                     |}"""
    check(selected)

    val text = """val x = 0
                 |val i = 0
                 |if (x.isInstanc<caret>eOf[Int]) {
                 |  x.asInstanceOf[Int].toString
                 |  println(x.asInstanceOf[Int])
                 |}"""
    val result = """val x = 0
                   |val i = 0
                   |x match {
                   |  case i1: Int =>
                   |    i1.toString
                   |    println(i1)
                   |  case _ =>
                   |}"""
    testQuickFix(text, result)
  }

  def test_10() {
    val selected = s"""val x = 0
                      |if (${START}x.isInstanceOf[Int]$END) x else if (x.isInstanceOf[Long]) x else 0"""
    check(selected)

    val text = """val x = 0
                 |if (x.isInstance<caret>Of[Int]) x else if (x.isInstanceOf[Long]) x else 0"""
    val result = """val x = 0
                   |x match {
                   |  case _: Int => x
                   |  case _: Long => x
                   |  case _ => 0
                   |}"""
    testQuickFix(text, result)
  }

  def test_11() {
    val selected = s"""import java.util
                     |val foo = (p: AnyVal) => {
                     |  if (${START}p.isInstanceOf[util.ArrayList[_]]$END) {}
                     |  else if (p.isInstanceOf[scala.util.control.Breaks]) {}
                     |}"""
    check(selected)

    val text = """import java.util
                 |val foo = (p: AnyVal) => {
                 |  if (p.isInstanceOf[util.ArrayList[_]]) {}
                 |  else if (p.isInstanceOf[scala.util.control.Breaks]) {}
                 |}"""
    val result = """import java.util
                   |val foo = (p: AnyVal) => {
                   |  p match {
                   |    case _: util.ArrayList[_] =>
                   |    case _: scala.util.control.Breaks =>
                   |    case _ =>
                   |  }
                   |}"""
    testQuickFix(text, result)
  }

  def test_12() {
    val selected = s"""def test2(p: AnyVal) {
                     |  if (${START}p.isInstanceOf[T forSome {type T <: Number}]$END) {}
                     |  else if (p.isInstanceOf[Int]) {}
                     |}"""
    check(selected)

    val text = """def test2(p: AnyVal) {
                 |  if (p.isInstanceOf[T forSome {type T <: Number}]) {}
                 |  else if (p.isInstanceOf[Int]) {}
                 |}"""
    val result = """def test2(p: AnyVal) {
                   |  p match {
                   |    case _: (T forSome {type T <: Number}) =>
                   |    case _: Int =>
                   |    case _ =>
                   |  }
                   |}"""
    testQuickFix(text, result)
  }

}
