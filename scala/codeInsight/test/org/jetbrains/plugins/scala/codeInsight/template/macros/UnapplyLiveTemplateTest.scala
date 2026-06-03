package org.jetbrains.plugins.scala.codeInsight.template.macros

class UnapplyLiveTemplateTest extends ScalaLiveTemplateTestBase with DoTestInCompanionObject {

  override protected def templateName = "unapply"

  def testUnapply(): Unit =
    doTestInCompanionObject(
      s"class A (x: Int, val y: String, override var z: Long = 42) {}",
      s"def unapply(arg: A): Option[(Int, String, Long)$CARET] = ???"
    )

  def testUnapplyWithSingleParameter(): Unit =
    doTestInCompanionObject(
      s"class A (x: Int) {}",
      s"def unapply(arg: A): Option[Int$CARET] = ???")

  def testUnapplyWithMultilineConstructorParamsWithComments(): Unit =
    doTestInCompanionObject(
      s"""class A (
         |  x: Int, // comment 1
         |  val y: String, // comment 2
         |  override var z: Long = 42 // comment 3
         |) {}
         |""".stripMargin,
      s"def unapply(arg: A): Option[(Int, String, Long)$CARET] = ???"
    )

  def testUnapplyForClassWithoutFields(): Unit =
    doTestInCompanionObject(
      s"class A () {}",
      s"def unapply(arg: A): Option[$CARET] = ???"
    )

  def testUnapplyShouldProcessParametersFromTheFirstClause(): Unit =
    doTestInCompanionObject(
      s"class A (p1: String, override val p2: Int)(p3: Long, p4: String) {}",
      s"""def unapply(arg: A): Option[(String, Int)$CARET] = ???""".stripMargin
    )

  def testUnapplyWithTupleParameters(): Unit =
    doTestInCompanionObject(
      s"class A (p1: (Int, Long), override val p2: Tuple4[Int, String, Int, Long])(p3: Long, p4: String) {}",
      s"def unapply(arg: A): Option[((Int, Long), Tuple4[Int, String, Int, Long])$CARET] = ???"
    )

  def testWithGenericParameters(): Unit =
    doTestInCompanionObject(
      "class X[T1, T2](val value: String, val seq: Seq[T1], val opt: Option[T2])",
      "def unapply[T1, T2](arg: X[T1, T2]): Option[(String, Seq[T1], Option[T2])] = ???"
    )

  def testWithGenericParametersWithBounds(): Unit =
    doTestInCompanionObject(
      "class X[T1 <: CharSequence, T2 >: String <: CharSequence](val value: String, val seq: Seq[T1], val opt: Option[T2])",
      "def unapply[T1 <: CharSequence, T2 >: String <: CharSequence](arg: X[T1, T2]): Option[(String, Seq[T1], Option[T2])] = ???"
    )
}
