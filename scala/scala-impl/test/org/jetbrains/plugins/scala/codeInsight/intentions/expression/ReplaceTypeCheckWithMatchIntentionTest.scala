package org.jetbrains.plugins.scala
package codeInsight.intentions.expression

import org.jetbrains.plugins.scala.codeInsight.intention.expression.ReplaceTypeCheckWithMatchIntention
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase

/**
 * Nikolay.Tropin
 * 5/16/13
 */
class ReplaceTypeCheckWithMatchIntentionTest extends ScalaIntentionTestBase {
  def familyName: String = ReplaceTypeCheckWithMatchIntention.familyName

  def test_1() {
    val text =
      """val x = 0
        |if (<caret>x.isInstanceOf[Int]) {
        |  x.toString
        |}"""
    val result =
      """val x = 0
        |x match {
        |  case _: Int =>
        |    x.toString
        |  case _ =>
        |}"""
    doTest(text, result)
  }

  def test_2() {
    val text =
      """val x = 0
        |if (x.isInstanc<caret>eOf[Int]) {
        |  x.asInstanceOf[Int].toString
        |  println(x.asInstanceOf[Int])
        |}"""
    val result =
      """val x = 0
        |x match {
        |  case i: Int =>
        |    i.toString
        |    println(i)
        |  case _ =>
        |}"""
    doTest(text, result)
  }

  def test_3() {
    val text =
      """val x = 0
        |if (x.isInstanc<caret>eOf[Int]) {
        |  val y = x.asInstanceOf[Int]
        |  x.asInstanceOf[Int].toString
        |  println(y)
        |}"""
    val result =
      """val x = 0
        |x match {
        |  case y: Int =>
        |    y.toString
        |    println(y)
        |  case _ =>
        |}"""
    doTest(text, result)
  }

  def test_4() {
    val text =
      """val x = 0
        |if (<caret>x.isInstanceOf[Int] && x.asInstanceOf[Int] == 1) {
        |  val y = x.asInstanceOf[Int]
        |  println(y)
        |}"""
    val result =
      """val x = 0
        |x match {
        |  case y: Int if y == 1 =>
        |    println(y)
        |  case _ =>
        |}"""
    doTest(text, result)
  }

  def test_5() {
    val text =
      """val x = 0
        |if (x > 0 && (<caret>x.isInstanceOf[Int] && x.asInstanceOf[Int] == 1)) {
        |  val y = x.asInstanceOf[Int]
        |  println(y)
        |}"""
    val result =
      """val x = 0
        |x match {
        |  case y: Int if x > 0 && y == 1 =>
        |    println(y)
        |  case _ =>
        |}"""
    doTest(text, result)
  }

  def test_6() {
    val text =
      """val x = 0
        |if (<caret>x.isInstanceOf[Int]) {
        |  val y = x.asInstanceOf[Int]
        |  println(y)
        |} else if (x.isInstanceOf[Long]) {
        |  println(x)
        |} else println()"""
    val result =
      """val x = 0
        |x match {
        |  case y: Int =>
        |    println(y)
        |  case _: Long =>
        |    println(x)
        |  case _ => println()
        |}"""
    doTest(text, result)
  }

  def test_7() {
    val text =
      """val x = 0
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
        |}"""
    doTest(text, result)
  }

  def test_8a() {
    val text =
      """val x1 = 0
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
        |}"""
    doTest(text, result)
  }

  def test_8b() {
    val text =
      """val x1 = 0
        |val x2 = 0
        |if (x1.isInstanceOf[Int] && <caret>x2.isInstanceOf[Int]) {
        |  val y1 = x1.asInstanceOf[Int]
        |  val y2 = x2.asInstanceOf[Int]
        |  println(y1 + y2)
        |} else if (x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
        |  val y1 = x1.asInstanceOf[Int]
        |  val y2 = x2.asInstanceOf[Int]
        |  println(y1 + y2)
        |}"""
    val result =
      """val x1 = 0
        |val x2 = 0
        |x2 match {
        |  case y2: Int if x1.isInstanceOf[Int] =>
        |    val y1 = x1.asInstanceOf[Int]
        |    println(y1 + y2)
        |  case _: Long if x1.isInstanceOf[Long] =>
        |    val y1 = x1.asInstanceOf[Int]
        |    val y2 = x2.asInstanceOf[Int]
        |    println(y1 + y2)
        |  case _ =>
        |} """
    doTest(text, result)
  }

  def test_8c() {
    val text =
      """val x1 = 0
        |val x2 = 0
        |if (x1.isInstanceOf[Int] && x2.isInstanceOf[Int]) {
        |  val y1 = x1.asInstanceOf[Int]
        |  val y2 = x2.asInstanceOf[Int]
        |  println(y1 + y2)
        |} else if (<caret>x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
        |  val y1 = x1.asInstanceOf[Int]
        |  val y2 = x2.asInstanceOf[Int]
        |  println(y1 + y2)
        |}"""
    val result =
      """val x1 = 0
        |val x2 = 0
        |if (x1.isInstanceOf[Int] && x2.isInstanceOf[Int]) {
        |  val y1 = x1.asInstanceOf[Int]
        |  val y2 = x2.asInstanceOf[Int]
        |  println(y1 + y2)
        |} else x1 match {
        |  case _: Long if x2.isInstanceOf[Long] =>
        |    val y1 = x1.asInstanceOf[Int]
        |    val y2 = x2.asInstanceOf[Int]
        |    println(y1 + y2)
        |  case _ =>
        |}
        | """
    doTest(text, result)
  }

  def test_9() {
    val text =
      """val x = 0
        |val i = 0
        |if (x.isInstanc<caret>eOf[Int]) {
        |  x.asInstanceOf[Int].toString
        |  println(x.asInstanceOf[Int])
        |}"""
    val result =
      """val x = 0
        |val i = 0
        |x match {
        |  case i1: Int =>
        |    i1.toString
        |    println(i1)
        |  case _ =>
        |}"""
    doTest(text, result)
  }


}
