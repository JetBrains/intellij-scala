package org.jetbrains.plugins.scala.codeInsight.template.macros

/**
 * @see scala/scala-impl/resources/liveTemplates/scala.xml
 */
class ApplyLiveTemplateTest extends ScalaLiveTemplateTestBase with DoTestInCompanionObject {

  override protected def templateName = "apply"

  def testApplyWithoutParameters(): Unit =
    doTestInCompanionObject(
      s"class A()",
      s"def apply($CARET): A = new A()"
    )

  def testApplySingleParameter(): Unit =
    doTestInCompanionObject(
      s"class A(param: Int)",
      s"def apply(param: Int$CARET): A = new A(param)"
    )

  def testApplyWithMultilineConstructorParamsWithComments(): Unit =
    doTestInCompanionObject(
      s"""case class SomeClass(param1: String,
         |                     val param2: Boolean,
         |                     var param3: Int,
         |                     override val param4: Long,
         |                     final override var param5: String) extends SomeTrait""".stripMargin,
      s"def apply(param1: String, param2: Boolean, param3: Int, param4: Long, param5: String$CARET): SomeClass = new SomeClass(param1, param2, param3, param4, param5)"
    )

  def testApplyShouldProcessAllParametersClausesAndPreserveImplicitModifier(): Unit = {
    doTestInCompanionObject(
      s"class A (p1: String, override val p2: Int)(implicit p3: Long, p4: String) {}",
      s"def apply(p1: String, p2: Int)(implicit p3: Long, p4: String$CARET): A = new A(p1, p2)(p3, p4)")
  }

  def testApplyWithTupleParameters(): Unit =
    doTestInCompanionObject(
      s"class A (p1: (Int, Long), override val p2: Tuple4[Int, String, Int, Long]) {}",
      s"def apply(p1: (Int, Long), p2: Tuple4[Int, String, Int, Long]$CARET): A = new A(p1, p2)"
    )

  def testWithGenericParameters(): Unit =
    doTestInCompanionObject(
      "class X[T1, T2](val value: String, val seq: Seq[T1], val opt: Option[T2])",
      "def apply[T1, T2](value: String, seq: Seq[T1], opt: Option[T2]): X[T1, T2] = new X(value, seq, opt)"
    )

  def testWithGenericParametersWithBounds(): Unit =
    doTestInCompanionObject(
      "class X[T1 <: CharSequence, T2 >: String <: CharSequence](val value: String, val seq: Seq[T1], val opt: Option[T2])",
      "def apply[T1 <: CharSequence, T2 >: String <: CharSequence](value: String, seq: Seq[T1], opt: Option[T2]): X[T1, T2] = new X(value, seq, opt)"
    )
}