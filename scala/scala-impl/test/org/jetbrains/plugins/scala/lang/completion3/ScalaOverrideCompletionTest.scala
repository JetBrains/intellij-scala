package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.SharedTestProjectToken
import org.jetbrains.plugins.scala.extensions.StringExt
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword.{DEF, OVERRIDE}
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings.{alwaysAddType, set}
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}

abstract class ScalaOverrideCompletionTestBase extends ScalaCompletionTestBase {

  override protected def sharedProjectToken = SharedTestProjectToken.ByTestClassAndScalaSdkAndProjectLibraries(this)

  protected override def setUp(): Unit = {
    super.setUp()
    set(getProject, alwaysAddType(getScalaCodeStyleSettings))
  }

  protected def checkNoOverrideCompletion(fileText: String, lookupString: String): Unit =
    super.checkNoCompletion(fileText) { lookup =>
      lookup.getLookupString.contains(OVERRIDE) &&
        lookup.getAllLookupStrings.contains(lookupString)
    }

  protected def doCompletionTest(fileText: String,
                                 resultText: String,
                                 items: String*): Unit =
    super.doRawCompletionTest(fileText, resultText) { lookup =>
      val lookupString = lookup.getLookupString
      items.forall(lookupString.contains)
    }

  protected def prepareFileText(fileText: String): String =
    s"""
       |trait Base {
       |  protected def foo(int: Int): Int = 45
       |  /**
       |    * text
       |    */
       |  type StringType = String
       |  val intValue = 45
       |  var intVariable: Int
       |  type A
       |  def abstractFoo
       |
       |  @throws(classOf[Exception])
       |  def annotFoo(int: Int): Int = 45
       |}
       |
       |${fileText.withNormalizedSeparator.trim}
    """.stripMargin

  override protected def configureFromFileText(fileText: String): PsiFile =
    super.configureFromFileText(prepareFileText(fileText))

  override protected def checkResultByText(expectedFileText: String, ignoreTrailingSpaces: Boolean): Unit =
    super.checkResultByText(prepareFileText(expectedFileText), ignoreTrailingSpaces)
}

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

  def testParamsFromTrait(): Unit = doCompletionTest(
    fileText = s"class Test(ov$CARET) extends Base",
    resultText = "class Test(override var intVariable: Int) extends Base",
    items = OVERRIDE, "intVariable"
  )
}

class ScalaOverrideCompletionTest2 extends ScalaCompletionTestBase {

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

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest
))
class ScalaOverrideCompletionTest_Scala3 extends ScalaCompletionTestBase {

  def testFunctionWithImplicitParameters(): Unit = doRawCompletionTest(
    fileText =
      s"""abstract class Base[T]:
         |  def myFunction(p: String, p2: T)(implicit c: CharSequence, t: T): String
         |
         |class Child extends Base[Short]:
         |  override def myFunction$CARET
         |""".stripMargin,
    resultText =
      s"""abstract class Base[T]:
        |  def myFunction(p: String, p2: T)(implicit c: CharSequence, t: T): String
        |
        |class Child extends Base[Short]:
        |  override def myFunction(p: String, p2: Short)(implicit c: CharSequence, t: Short): String = $CARET$START???$END
        |""".stripMargin
  )()

  def testFunctionWithUsingParameters(): Unit = doRawCompletionTest(
    fileText =
      s"""abstract class Base[T]:
         |  def myFunction(p: String, p2: T)(using c: CharSequence, t: T): String
         |
         |class Child extends Base[Short]:
         |  override def myFunction$CARET
         |""".stripMargin,
    resultText =
      s"""abstract class Base[T]:
         |  def myFunction(p: String, p2: T)(using c: CharSequence, t: T): String
         |
         |class Child extends Base[Short]:
         |  override def myFunction(p: String, p2: Short)(using c: CharSequence, t: Short): String = $CARET$START???$END
         |""".stripMargin
  )()
}

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
