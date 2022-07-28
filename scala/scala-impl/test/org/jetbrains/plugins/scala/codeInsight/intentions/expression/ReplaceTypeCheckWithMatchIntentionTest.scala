package org.jetbrains.plugins.scala
package codeInsight
package intentions
package expression

class ReplaceTypeCheckWithMatchIntentionTest extends ScalaIntentionTestBase {

  override def familyName: String = ScalaBundle.message("family.name.replace.type.check.with.pattern.matching")

  def test_1(): Unit = doTest(
    s"""val x = 0
       |if (${CARET}x.isInstanceOf[Int]) {
       |  x.toString
       |}""".stripMargin,
    """val x = 0
      |x match {
      |  case _: Int =>
      |    x.toString
      |  case _ =>
      |}""".stripMargin
  )

  def test_2(): Unit = doTest(
    s"""val x = 0
       |if (x.isInstanc${CARET}eOf[Int]) {
       |  x.asInstanceOf[Int].toString
       |  println(x.asInstanceOf[Int])
       |}""".stripMargin,
    """val x = 0
      |x match {
      |  case i: Int =>
      |    i.toString
      |    println(i)
      |  case _ =>
      |}""".stripMargin
  )

  def test_3(): Unit = doTest(
    s"""val x = 0
       |if (x.isInstanc${CARET}eOf[Int]) {
       |  val y = x.asInstanceOf[Int]
       |  x.asInstanceOf[Int].toString
       |  println(y)
       |}""".stripMargin,
    """val x = 0
      |x match {
      |  case y: Int =>
      |    y.toString
      |    println(y)
      |  case _ =>
      |}""".stripMargin
  )

  def test_4(): Unit = doTest(
    s"""val x = 0
       |if (${CARET}x.isInstanceOf[Int] && x.asInstanceOf[Int] == 1) {
       |  val y = x.asInstanceOf[Int]
       |  println(y)
       |}""".stripMargin,
    """val x = 0
      |x match {
      |  case y: Int if y == 1 =>
      |    println(y)
      |  case _ =>
      |}""".stripMargin
  )

  def test_5(): Unit = doTest(
    s"""val x = 0
       |if (x > 0 && (${CARET}x.isInstanceOf[Int] && x.asInstanceOf[Int] == 1)) {
       |  val y = x.asInstanceOf[Int]
       |  println(y)
       |}""".stripMargin,
    """val x = 0
      |x match {
      |  case y: Int if y == 1 && x > 0 =>
      |    println(y)
      |  case _ =>
      |}""".stripMargin
  )

  def test_6(): Unit = doTest(
    s"""val x = 0
       |if (${CARET}x.isInstanceOf[Int]) {
       |  val y = x.asInstanceOf[Int]
       |  println(y)
       |} else if (x.isInstanceOf[Long]) {
       |  println(x)
       |} else println()""".stripMargin,
    """val x = 0
      |x match {
      |  case y: Int =>
      |    println(y)
      |  case _: Long =>
      |    println(x)
      |  case _ => println()
      |}""".stripMargin
  )

  def test_7(): Unit = doTest(
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
       |}""".stripMargin,
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
  )

  def test_8a(): Unit = doTest(
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
       |}""".stripMargin,
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
  )

  def test_8b(): Unit = doTest(
    s"""val x1 = 0
       |val x2 = 0
       |if (x1.isInstanceOf[Int] && ${CARET}x2.isInstanceOf[Int]) {
       |  val y1 = x1.asInstanceOf[Int]
       |  val y2 = x2.asInstanceOf[Int]
       |  println(y1 + y2)
       |} else if (x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
       |  val y1 = x1.asInstanceOf[Int]
       |  val y2 = x2.asInstanceOf[Int]
       |  println(y1 + y2)
       |}""".stripMargin,
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
      |}""".stripMargin
  )

  def test_8c(): Unit = doTest(
    s"""val x1 = 0
       |val x2 = 0
       |if (x1.isInstanceOf[Int] && x2.isInstanceOf[Int]) {
       |  val y1 = x1.asInstanceOf[Int]
       |  val y2 = x2.asInstanceOf[Int]
       |  println(y1 + y2)
       |} else if (${CARET}x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
       |  val y1 = x1.asInstanceOf[Int]
       |  val y2 = x2.asInstanceOf[Int]
       |  println(y1 + y2)
       |}""".stripMargin,
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
      |}""".stripMargin
  )

  def test_9(): Unit = doTest(
    s"""val x = 0
       |val i = 0
       |if (x.isInstanc${CARET}eOf[Int]) {
       |  x.asInstanceOf[Int].toString
       |  println(x.asInstanceOf[Int])
       |}""".stripMargin,
    """val x = 0
      |val i = 0
      |x match {
      |  case i1: Int =>
      |    i1.toString
      |    println(i1)
      |  case _ =>
      |}""".stripMargin
  )
}
