package org.jetbrains.plugins.scala.codeInsight.template.macros

class ApplyLiveTemplateTest extends ScalaLiveTemplateTestBase {
  override protected def templateName = "apply"

  def testApplyWithoutParameters(): Unit = {
    val before =
      s"""class A()
         |object A {
         |  $CARET
         |}
         |""".stripMargin
    val after =
      s"""class A()
         |object A {
         |  def apply($CARET): A = new A()
         |}
         |""".stripMargin
    doTest(before, after)
  }

  def testApplySingleParameter(): Unit = {
    val before =
      s"""class A(param: Int)
         |object A {
         |  $CARET
         |}
         |""".stripMargin
    val after =
      s"""class A(param: Int)
         |object A {
         |  def apply(param: Int$CARET): A = new A(param)
         |}
         |""".stripMargin
    doTest(before, after)
  }

  def testApplyWithMultilineConstructorParamsWithComments(): Unit = {
    val before =
      s"""case class SomeClass(param1: String,
         |                     val param2: Boolean,
         |                     var param3: Int,
         |                     override val param4: Long,
         |                     final override var param5: String) extends SomeTrait
         |object SomeClass {
         |  $CARET
         |}
         |""".stripMargin
    val after =
      s"""case class SomeClass(param1: String,
         |                     val param2: Boolean,
         |                     var param3: Int,
         |                     override val param4: Long,
         |                     final override var param5: String) extends SomeTrait
         |object SomeClass {
         |  def apply(param1: String, param2: Boolean, param3: Int, param4: Long, param5: String$CARET): SomeClass = new SomeClass(param1, param2, param3, param4, param5)
         |}
         |""".stripMargin
    doTest(before, after)
  }

  def testApplyShouldProcessAllParametersClausesAndPreserveImplicitModifire(): Unit = {
    val before =
      s"""class A (p1: String, override val p2: Int)(implicit p3: Long, p4: String) {}
         |object A {
         |  $CARET
         |}
         |""".stripMargin
    val after =
      s"""class A (p1: String, override val p2: Int)(implicit p3: Long, p4: String) {}
         |object A {
         |  def apply(p1: String, p2: Int)(implicit p3: Long, p4: String$CARET): A = new A(p1, p2)(p3, p4)
         |}
         |""".stripMargin
    doTest(before, after)
  }

  def testApplyWithTupleParameters(): Unit = {
    val before =
      s"""class A (p1: (Int, Long), override val p2: Tuple4[Int, String, Int, Long]) {}
         |object A {
         |  $CARET
         |}
         |""".stripMargin
    val after =
      s"""class A (p1: (Int, Long), override val p2: Tuple4[Int, String, Int, Long]) {}
         |object A {
         |  def apply(p1: (Int, Long), p2: Tuple4[Int, String, Int, Long]$CARET): A = new A(p1, p2)
         |}
         |""".stripMargin
    doTest(before, after)
  }

}
