package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword.{DEF, OVERRIDE}

class ScalaOverrideCompletionTest extends ScalaOverrideCompletionTestBase {

  def testFunction(): Unit = doRawCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   override def f$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |class Inheritor extends Base {
        |  override def foo(int: Int): Int = super.foo(int)
        |}
      """.stripMargin
  )()

  def testValue(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   override val intVa$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |class Inheritor extends Base {
        |  override val intValue: Int = ???
        |}
      """.stripMargin,
    items = "intValue"
  )

  def testVariable(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   override var i$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |class Inheritor extends Base {
        |  override var intVariable: Int = ???
        |}
      """.stripMargin,
    items = "intVariable"
  )

  def testJavaObjectMethod(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   override def h$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |class Inheritor extends Base {
        |  override def hashCode(): Int = super.hashCode()
        |}
      """.stripMargin,
    items = "hashCode"
  )

  def testOverrideKeyword(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   over$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |class Inheritor extends Base {
        |  override protected def foo(int: Int): Int = super.foo(int)
        |}
      """.stripMargin,
    items = OVERRIDE, DEF, "foo"
  )

  def testAbstractType(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   override type $CARET
         |}
      """.stripMargin,
    resultText =
      """
        |class Inheritor extends Base {
        |  override type A = this.type
        |}
      """.stripMargin,
    items = "A"
  )

  def testAbstractFunction(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   override protected def $CARET
         |}
      """.stripMargin,
    resultText =
      """
        |class Inheritor extends Base {
        |  override protected def abstractFoo: Unit = ???
        |}
      """.stripMargin,
    items = "abstractFoo"
  )

  def testAllowOverrideFunctionWithoutOverrideKeyword(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   protected def a$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |class Inheritor extends Base {
        |  override protected def abstractFoo: Unit = ???
        |}
      """.stripMargin,
    items = "abstractFoo"
  )

  def testAllowOverrideVariableWithoutOverrideKeyword(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   var i$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |class Inheritor extends Base {
        |  override var intVariable: Int = ???
        |}
      """.stripMargin,
    items = "intVariable"
  )

  def testNoMethodCompletionInClassParameter(): Unit = checkNoOverrideCompletion(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   class A(ab$CARET)
         |}
      """.stripMargin,
    lookupString = "abstractFoo"
  )

  def testNoCompletionAfterDot(): Unit = checkNoOverrideCompletion(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   var i = 12.ab$CARET
         |}
      """.stripMargin,
    lookupString = "abstractFoo"
  )

  //Like in java, don't save annotations here
  def testWithAnnotation(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   annotFoo$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |class Inheritor extends Base {
        |  override def annotFoo(int: Int): Int = super.annotFoo(int)
        |}
      """.stripMargin,
    items = OVERRIDE, "annotFoo"
  )

  def testNoCompletionInFunction(): Unit = checkNoOverrideCompletion(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   def outherFunc(): Unit = {
         |     annotFoo$CARET
         |   }
         |}
      """.stripMargin,
    lookupString = "abstractFoo"
  )

  def testNoCompletionInModifier(): Unit = checkNoOverrideCompletion(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   private[intV$CARET] val test = 123
         |}
      """.stripMargin,
    lookupString = "intValue"
  )

  def testNoCompletionAfterColon(): Unit = checkNoOverrideCompletion(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   val test: intV$CARET = 123
         |}
      """.stripMargin,
    lookupString = "intValue"
  )

  //SCL-11519
  def testParamsFromTrait(): Unit = doCompletionTest(
    fileText = s"class Test(ov$CARET) extends Base",
    resultText = "class Test(override var intVariable: Int) extends Base",
    items = OVERRIDE, "intVariable"
  )

  //SCL-11519
  def testParamsFromClass(): Unit = doRawCompletionTest(
    fileText =
      s"""
         |class Person(val name: String) {
         |  val gender: Boolean = true
         |  val age: Int = 45
         |}
         |
         |case class ExamplePerson(override val nam$CARET, override val age: Int, override val gender: Boolean) extends Person("") {
         |}
      """.stripMargin,
    resultText =
      """
        |class Person(val name: String) {
        |  val gender: Boolean = true
        |  val age: Int = 45
        |}
        |
        |case class ExamplePerson(override val name: String, override val age: Int, override val gender: Boolean) extends Person("") {
        |}
      """.stripMargin
  )()
}
