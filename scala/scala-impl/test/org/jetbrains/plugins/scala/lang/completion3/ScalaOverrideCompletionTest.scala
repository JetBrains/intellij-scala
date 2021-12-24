package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword.{DEF, OVERRIDE}
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings.{alwaysAddType, set}
import org.jetbrains.plugins.scala.util.runners.{RunWithScalaVersions, TestScalaVersion}

abstract class ScalaOverrideCompletionTestBase extends ScalaCodeInsightTestBase {

  import ScalaOverrideCompletionTestBase._

  protected override def setUp(): Unit = {
    super.setUp()
    set(getProject, alwaysAddType(getScalaSettings))
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

  protected def prepareFileText(fileText: String): String = testText(fileText)

  override protected def configureFromFileText(fileText: String): PsiFile =
    super.configureFromFileText(prepareFileText(fileText))

  override protected def checkResultByText(expectedFileText: String, ignoreTrailingSpaces: Boolean): Unit =
    super.checkResultByText(prepareFileText(expectedFileText), ignoreTrailingSpaces)
}

object ScalaOverrideCompletionTestBase {

  private def testText(fileText: String): String =
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
       |${normalize(fileText)}
    """
}

/**
  * Created by kate
  * on 3/11/16
  */
class ScalaOverrideCompletionTest extends ScalaOverrideCompletionTestBase {

  def testFunction(): Unit = doRawCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   override def f$CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends Base {
        |  override def foo(int: Int): Int = super.foo(int)
        |}
      """
  )()

  def testValue(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   override val intVa$CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends Base {
        |  override val intValue: Int = _
        |}
      """,
    items = "intValue"
  )

  def testVariable(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   override var i$CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends Base {
        |  override var intVariable: Int = _
        |}
      """,
    items = "intVariable"
  )

  def testJavaObjectMethod(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   override def h$CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends Base {
        |  override def hashCode(): Int = super.hashCode()
        |}
      """,
    items = "hashCode"
  )

  def testOverrideKeyword(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   over$CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends Base {
        |  override protected def foo(int: Int): Int = super.foo(int)
        |}
      """,
    items = OVERRIDE, DEF, "foo"
  )

  def testAbstractType(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   override type $CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends Base {
        |  override type A = this.type
        |}
      """,
    items = "A"
  )

  def testAbstractFunction(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   override protected def $CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends Base {
        |  override protected def abstractFoo: Unit = ???
        |}
      """,
    items = "abstractFoo"
  )

  def testAllowOverrideFunctionWithoutOverrideKeyword(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   protected def a$CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends Base {
        |  override protected def abstractFoo: Unit = ???
        |}
      """,
    items = "abstractFoo"
  )

  def testAllowOverrideVariableWithoutOverrideKeyword(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   var i$CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends Base {
        |  override var intVariable: Int = _
        |}
      """,
    items = "intVariable"
  )

  def testNoMethodCompletionInClassParameter(): Unit = checkNoOverrideCompletion(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   class A(ab$CARET)
         |}
      """,
    lookupString = "abstractFoo"
  )

  def testNoCompletionAfterDot(): Unit = checkNoOverrideCompletion(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   var i = 12.ab$CARET
         |}
      """,
    lookupString = "abstractFoo"
  )

  //Like in java, don't save annotations here
  def testWithAnnotation(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   annotFoo$CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends Base {
        |  override def annotFoo(int: Int): Int = super.annotFoo(int)
        |}
      """,
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
      """,
    lookupString = "abstractFoo"
  )

  def testNoCompletionInModifier(): Unit = checkNoOverrideCompletion(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   private[intV$CARET] val test = 123
         |}
      """,
    lookupString = "intValue"
  )

  def testNoCompletionAfterColon(): Unit = checkNoOverrideCompletion(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   val test: intV$CARET = 123
         |}
      """,
    lookupString = "intValue"
  )

  def testParamsFromTrait(): Unit = doCompletionTest(
    fileText = s"class Test(ov$CARET) extends Base",
    resultText = "class Test(override var intVariable: Int) extends Base",
    items = OVERRIDE, "intVariable"
  )
}

class ScalaOverrideCompletionTest2 extends ScalaCodeInsightTestBase {

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
      """,
    resultText =
      """
        |class Person(val name: String) {
        |  val gender: Boolean = true
        |  val age: Int = 45
        |}
        |
        |case class ExamplePerson(override val name: String, override val age: Int, override val gender: Boolean) extends Person("") {
        |}
      """
  )()
}

@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_3_Latest
))
class ScalaOverrideTargetNameCompletionTest extends ScalaOverrideCompletionTestBase {

  import ScalaOverrideTargetNameCompletionTest._

  def testFunction(): Unit = doRawCompletionTest(
    fileText =
      s"""
         |class Inheritor extends BaseTrait {
         |   override def f$CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends BaseTrait {
        |  @targetName("boo")
        |  override def foo(int: Int): Int = super.foo(int)
        |}
      """
  )()

  def testValue(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends BaseTrait {
         |   override val intVa$CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends BaseTrait {
        |  @targetName("extIntValue")
        |  override val intValue: Int = _
        |}
      """,
    items = "intValue"
  )

  def testVariable(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends BaseTrait {
         |   override var i$CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends BaseTrait {
        |  @targetName("extIntVariable")
        |  override var intVariable: Int = _
        |}
      """,
    items = "intVariable"
  )

  def testAbstractFunction(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends BaseTrait {
         |   override protected def $CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends BaseTrait {
        |  @targetName("extAbstractFoo")
        |  override protected def abstractFoo: Unit = ???
        |}
      """,
    items = "abstractFoo"
  )

  def testWithAnnotation(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends BaseTrait {
         |   annotFoo$CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends BaseTrait {
        |  @targetName("extAnnotFoo")
        |  override def annotFoo(int: Int): Int = super.annotFoo(int)
        |}
      """,
    items = OVERRIDE, "annotFoo"
  )

  def testType(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends BaseTrait {
         |   override type Str$CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends BaseTrait {
        |  @targetName("ExtStringType")
        |  override type StringType = String
        |}
      """,
    items = "StringType"
  )

  def testAbstractType(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends BaseTrait {
         |   override type $CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends BaseTrait {
        |  @targetName("ExtA")
        |  override type A = this.type
        |}
      """,
    items = "A"
  )

  def testParamsFromClass(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor(override val f$CARET) extends BaseClass {
         |}
      """,
    resultText =
      """
        |class Inheritor(@targetName("boo") override val foo: Int) extends BaseClass {
        |}
      """,
    items = "foo"
  )

  def testParamsFromClassInCaseClass(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor(b$CARET) extends BaseClass {
         |}
      """,
    resultText =
      """
        |class Inheritor(@targetName("far") override val bar: String) extends BaseClass {
        |}
      """,
    items = "bar"
  )

  def testOverrideKeyword(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends BaseTrait {
         |   over$CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends BaseTrait {
        |  @targetName("boo")
        |  override protected def foo(int: Int): Int = super.foo(int)
        |}
      """,
    items = OVERRIDE, DEF, "foo"
  )

  def testAllowOverrideFunctionWithoutOverrideKeyword(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends BaseTrait {
         |   protected def a$CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends BaseTrait {
        |  @targetName("extAbstractFoo")
        |  override protected def abstractFoo: Unit = ???
        |}
      """,
    items = "abstractFoo"
  )

  def testAllowOverrideVariableWithoutOverrideKeyword(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends BaseTrait {
         |   var i$CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends BaseTrait {
        |  @targetName("extIntVariable")
        |  override var intVariable: Int = _
        |}
      """,
    items = "intVariable"
  )

  def testDoNotAddTargetNameIfAlreadyPresent(): Unit = doCompletionTest(
    fileText =
      s"""
         |class Inheritor extends BaseTrait {
         |   @targetName("anotherExtIntValue") val i$CARET
         |}
      """,
    resultText =
      """
        |class Inheritor extends BaseTrait {
        |  @targetName("anotherExtIntValue") override val intValue: Int = _
        |}
      """,
    items = "intValue"
  )

  override protected def prepareFileText(fileText: String) = testText(fileText)
}

object ScalaOverrideTargetNameCompletionTest {
  private def testText(fileText: String): String =
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
       |${normalize(fileText)}
    """
}
