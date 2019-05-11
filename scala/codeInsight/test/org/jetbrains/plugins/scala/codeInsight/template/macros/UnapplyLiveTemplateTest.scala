package org.jetbrains.plugins.scala.codeInsight.template.macros

class UnapplyLiveTemplateTest extends ScalaLiveTemplateTestBase {
  override protected def templateName = "unapply"

  def testUnapply(): Unit = {
    val before =
      s"""class A (x: Int, val y: String, override var z: Long = 42) {}
         |object A {
         |  $CARET
         |}
         |""".stripMargin
    val after =
      s"""class A (x: Int, val y: String, override var z: Long = 42) {}
         |object A {
         |  def unapply(arg: A): Option[(Int, String, Long)$CARET] = ???
         |}
         |""".stripMargin
    doTest(before, after)
  }

  def testUnapplyWithSingleParameter(): Unit = {
    val before =
      s"""class A (x: Int) {}
         |object A {
         |  $CARET
         |}
         |""".stripMargin
    val after =
      s"""class A (x: Int) {}
         |object A {
         |  def unapply(arg: A): Option[Int$CARET] = ???
         |}
         |""".stripMargin
    doTest(before, after)
  }

  def testUnapplyWithMultilineConstructorParamsWithComments(): Unit = {
    val before =
      s"""class A (
         |  x: Int, // comment 1
         |  val y: String, // comment 2
         |  override var z: Long = 42 // comment 3
         |) {}
         |object A {
         |  $CARET
         |}
         |""".stripMargin
    val after =
      s"""class A (
         |  x: Int, // comment 1
         |  val y: String, // comment 2
         |  override var z: Long = 42 // comment 3
         |) {}
         |object A {
         |  def unapply(arg: A): Option[(Int, String, Long)$CARET] = ???
         |}
         |""".stripMargin
    doTest(before, after)
  }

  def testUnapplyForClassWithoutFields(): Unit = {
    val before =
      s"""class A () {}
         |object A {
         |  $CARET
         |}
         |""".stripMargin
    val after =
      s"""class A () {}
         |object A {
         |  def unapply(arg: A): Option[$CARET] = ???
         |}
         |""".stripMargin
    doTest(before, after)
  }


  def testUnapplyShouldProcessParametersFromTheFirstClause(): Unit = {
    val before =
      s"""class A (p1: String, override val p2: Int)(p3: Long, p4: String) {}
         |object A {
         |  $CARET
         |}
         |""".stripMargin
    val after =
      s"""class A (p1: String, override val p2: Int)(p3: Long, p4: String) {}
         |object A {
         |  def unapply(arg: A): Option[(String, Int)$CARET] = ???
         |}
         |""".stripMargin
    doTest(before, after)
  }

  def testUnapplyWithTupleParameters(): Unit = {
    val before =
      s"""class A (p1: (Int, Long), override val p2: Tuple4[Int, String, Int, Long])(p3: Long, p4: String) {}
         |object A {
         |  $CARET
         |}
         |""".stripMargin
    val after =
      s"""class A (p1: (Int, Long), override val p2: Tuple4[Int, String, Int, Long])(p3: Long, p4: String) {}
         |object A {
         |  def unapply(arg: A): Option[((Int, Long), Tuple4[Int, String, Int, Long])$CARET] = ???
         |}
         |""".stripMargin
    doTest(before, after)
  }
}
