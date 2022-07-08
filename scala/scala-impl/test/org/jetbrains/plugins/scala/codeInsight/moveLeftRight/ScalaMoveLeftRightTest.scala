package org.jetbrains.plugins.scala.codeInsight.moveLeftRight

class ScalaMoveLeftRightTest extends ScalaMoveLeftRightTestBase {
  def testMethodArgs(): Unit = {
    doTestFromLeftToRight(
      "Seq(<caret>1, 22, 333)",
      "Seq(22, <caret>1, 333)",
      "Seq(22, 333, <caret>1)"
    )
  }

  def testMethodParams(): Unit = {
    doTestFromRightToLeft(
      "def example(s: String, i: Int, <caret>b: Boolean): Unit = {}",
      "def example(s: String, <caret>b: Boolean, i: Int): Unit = {}",
      "def example(<caret>b: Boolean, s: String, i: Int): Unit = {}"
    )
  }

  def testClassParams(): Unit = {
    doTestFromLeftToRight(
      "class Person(val id<caret>: Long, name: String)",
      "class Person(name: String, val id<caret>: Long)"
    )
  }

  def testTypeParams(): Unit = {
    doTestFromLeftToRight(
      "class Pair[T<caret>1, T2](t1: T1, t2: T2)",
      "class Pair[T2, T<caret>1](t1: T1, t2: T2)"
    )
  }

  def testTypeArgs(): Unit = {
    doTestFromLeftToRight(
      "new Pair[Int<caret>, Boolean](0, true)",
      "new Pair[Boolean, Int<caret>](0, true)"
    )
  }

  def testPatternArgs(): Unit = {
    doTestFromRightToLeft (
      "val List(x, <caret>y) = List(1, 2)",
      "val List(<caret>y, x) = List(1, 2)"
    )
  }

  def testTuple(): Unit = {
    doTestFromLeftToRight(
      "val (x, y) = (<caret>1, 2)",
      "val (x, y) = (2, <caret>1)"
    )
  }

  def testTuplePattern(): Unit = {
    doTestFromLeftToRight(
      "val (<caret>x, y) = (1, 2)",
      "val (y, <caret>x) = (1, 2)"
    )
  }

  def testTupleType(): Unit = {
    doTestFromLeftToRight(
      "val x: Option[(<caret>Int, String)] = None",
      "val x: Option[(String, <caret>Int)] = None"
    )
  }

  def testCaseClauses(): Unit = {
    doTestFromRightToLeft(
      """1 match {
        |  case 0 => false
        |  case <caret>1 => true
        |}""".stripMargin.replace("\r", ""),
      """1 match {
        |  case <caret>1 => true
        |  case 0 => false
        |}""".stripMargin.replace("\r", "")
    )
  }

  def testInfixExpr(): Unit ={
    doTestFromLeftToRight(
      "<caret>1 + 2 + 3",
      "2 + <caret>1 + 3",
      "2 + 3 + <caret>1"
    )
  }

  def testInfixExprDifferentPriority(): Unit = {
    doTestFromLeftToRight(
      "1 + <caret>2 * 3 + 3 * 4",
      "1 + 3 * <caret>2 + 3 * 4"
    )
    doTestFromRightToLeft(
      "1 + 2 * 3 + 3 <caret>* 4",
      "1 + 3 <caret>* 4 + 2 * 3",
      "3 <caret>* 4 + 1 + 2 * 3"
    )
  }

  def testInfixExprNonOperator(): Unit = {
    checkMoveRightIsDisabled("Se<caret>q(1) foreach println")
  }

  def testInfixExprAssignOperator(): Unit = {
    checkMoveRightIsDisabled("<caret>x += 1 + 2 + 3")
    doTestFromLeftToRight(
      "x += <caret>1 + 2 + 3",
      "x += 2 + <caret>1 + 3",
      "x += 2 + 3 + <caret>1"
    )
  }

  def testInfixPattern(): Unit = {
    doTestFromLeftToRight(
      "val <caret>x :: y :: Nil = 1 :: 2 :: Nil",
      "val y :: <caret>x :: Nil = 1 :: 2 :: Nil",
      "val y :: Nil :: <caret>x = 1 :: 2 :: Nil"
    )
  }

  def testInfixType(): Unit = {
    doTestFromLeftToRight(
      "val hList: <caret>String :: Int :: HNil = ???",
      "val hList: Int :: <caret>String :: HNil = ???",
      "val hList: Int :: HNil :: <caret>String = ???"
    )
  }

}
