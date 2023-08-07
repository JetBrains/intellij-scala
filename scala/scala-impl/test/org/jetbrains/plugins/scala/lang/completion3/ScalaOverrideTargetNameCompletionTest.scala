package org.jetbrains.plugins.scala.lang.completion3

import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword.{DEF, OVERRIDE}
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest
))
class ScalaOverrideTargetNameCompletionTest extends ScalaOverrideCompletionTestBase {

  def testFunction(): Unit = doRawCompletionTest(
    fileText =
      s"""class Inheritor extends BaseTrait {
         |   override def f$CARET
         |}
      """.stripMargin,
    resultText =
      """class Inheritor extends BaseTrait {
        |  @targetName("boo")
        |  override def foo(int: Int): Int = super.foo(int)
        |}
      """.stripMargin
  )()

  def testValue(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends BaseTrait {
         |   override val intVa$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |class Inheritor extends BaseTrait {
        |  @targetName("extIntValue")
        |  override val intValue: Int = ???
        |}
      """.stripMargin,
    items = "intValue"
  )

  def testVariable(): Unit = doCompletionTest(
    fileText =
      s"""class Inheritor extends BaseTrait {
         |   override var i$CARET
         |}""".stripMargin,
    resultText =
      """class Inheritor extends BaseTrait {
        |  @targetName("extIntVariable")
        |  override var intVariable: Int = ???
        |}""".stripMargin,
    items = "intVariable"
  )

  def testAbstractFunction(): Unit = doCompletionTest(
    fileText =
      s"""class Inheritor extends BaseTrait {
         |   override protected def $CARET
         |}
      """.stripMargin,
    resultText =
      """class Inheritor extends BaseTrait {
        |  @targetName("extAbstractFoo")
        |  override protected def abstractFoo: Unit = ???
        |}
      """.stripMargin,
    items = "abstractFoo"
  )

  def testWithAnnotation(): Unit = doCompletionTest(
    fileText =
      s"""class Inheritor extends BaseTrait {
         |   annotFoo$CARET
         |}""".stripMargin,
    resultText =
      """class Inheritor extends BaseTrait {
        |  @targetName("extAnnotFoo")
        |  override def annotFoo(int: Int): Int = super.annotFoo(int)
        |}""".stripMargin,
    items = OVERRIDE, "annotFoo"
  )

  def testType(): Unit = doCompletionTest(
    fileText =
      s"""class Inheritor extends BaseTrait {
         |   override type Str$CARET
         |}
      """.stripMargin,
    resultText =
      """class Inheritor extends BaseTrait {
        |  @targetName("ExtStringType")
        |  override type StringType = String
        |}
      """.stripMargin,
    items = "StringType"
  )

  def testAbstractType(): Unit = doCompletionTest(
    fileText =
      s"""class Inheritor extends BaseTrait {
         |   override type $CARET
         |}
      """.stripMargin,
    resultText =
      """class Inheritor extends BaseTrait {
        |  @targetName("ExtA")
        |  override type A = this.type
        |}
      """.stripMargin,
    items = "A"
  )

  def testParamsFromClass(): Unit = doCompletionTest(
    fileText =
      s"""class Inheritor(override val f$CARET) extends BaseClass {
         |}
      """.stripMargin,
    resultText =
      """class Inheritor(@targetName("boo") override val foo: Int) extends BaseClass {
        |}
      """.stripMargin,
    items = "foo"
  )

  def testParamsFromClassInCaseClass(): Unit = doCompletionTest(
    fileText =
      s"""class Inheritor(b$CARET) extends BaseClass {
         |}
      """.stripMargin,
    resultText =
      """class Inheritor(@targetName("far") override val bar: String) extends BaseClass {
        |}
      """.stripMargin,
    items = "bar"
  )

  def testOverrideKeyword(): Unit = doCompletionTest(
    fileText =
      s"""class Inheritor extends BaseTrait {
         |   over$CARET
         |}
      """.stripMargin,
    resultText =
      """class Inheritor extends BaseTrait {
        |  @targetName("boo")
        |  override protected def foo(int: Int): Int = super.foo(int)
        |}
      """.stripMargin,
    items = OVERRIDE, DEF, "foo"
  )

  def testAllowOverrideFunctionWithoutOverrideKeyword(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends BaseTrait {
         |   protected def a$CARET
         |}
      """.stripMargin,
    resultText =
      """
        |class Inheritor extends BaseTrait {
        |  @targetName("extAbstractFoo")
        |  override protected def abstractFoo: Unit = ???
        |}
      """.stripMargin,
    items = "abstractFoo"
  )

  def testAllowOverrideVariableWithoutOverrideKeyword(): Unit = doCompletionTest(
    fileText =
      s"""class Inheritor extends BaseTrait {
         |   var i$CARET
         |}
      """.stripMargin,
    resultText =
      """class Inheritor extends BaseTrait {
        |  @targetName("extIntVariable")
        |  override var intVariable: Int = ???
        |}
      """.stripMargin,
    items = "intVariable"
  )

  def testDoNotAddTargetNameIfAlreadyPresent(): Unit = doCompletionTest(
    fileText =
      s"""class Inheritor extends BaseTrait {
         |   @targetName("anotherExtIntValue") val i$CARET
         |}
      """.stripMargin,
    resultText =
      """class Inheritor extends BaseTrait {
        |  @targetName("anotherExtIntValue") override val intValue: Int = ???
        |}
      """.stripMargin,
    items = "intValue"
  )

  override protected def prepareFileText(fileText: String) =
    s"""import scala.annotation.targetName
       |
       |trait BaseTrait {
       |  @targetName("boo")
       |  protected def foo(int: Int): Int = 45
       |  @targetName("ExtStringType")
       |  type StringType = String
       |  @targetName("extIntValue")
       |  val intValue = 45
       |  @targetName("extIntVariable")
       |  var intVariable: Int
       |  @targetName("ExtA")
       |  type A
       |  @targetName("extAbstractFoo")
       |  def abstractFoo
       |
       |  @throws(classOf[Exception])
       |  @targetName("extAnnotFoo")
       |  def annotFoo(int: Int): Int = 45
       |}
       |
       |class BaseClass(@targetName("boo") val foo: Int, @targetName("far") val bar: String = "...")
       |
       |$fileText
    """.stripMargin
}
