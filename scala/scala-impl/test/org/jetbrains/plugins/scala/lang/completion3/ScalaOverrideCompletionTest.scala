package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings.alwaysAddType
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings.set

/**
  * Created by kate
  * on 3/11/16
  */
class ScalaOverrideCompletionTest extends ScalaCodeInsightTestBase {

  import ScalaKeyword._
  import ScalaOverrideCompletionTest._

  protected override def setUp(): Unit = {
    super.setUp()
    set(getProject, alwaysAddType(getScalaSettings))
  }

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

  private def checkNoOverrideCompletion(fileText: String, lookupString: String): Unit =
    super.checkNoCompletion(fileText) { lookup =>
      lookup.getLookupString.contains(OVERRIDE) &&
        lookup.getAllLookupStrings.contains(lookupString)
    }

  private def doCompletionTest(fileText: String,
                               resultText: String,
                               items: String*): Unit = {
    super.doRawCompletionTest(fileText, resultText) { lookup =>
      val lookupString = lookup.getLookupString
      items.forall(lookupString.contains)
    }
  }

  override protected def configureFromFileText(fileText: String): PsiFile =
    super.configureFromFileText(testText(fileText))

  override protected def checkResultByText(expectedFileText: String, ignoreTrailingSpaces: Boolean): Unit =
    super.checkResultByText(testText(expectedFileText), ignoreTrailingSpaces)
}

object ScalaOverrideCompletionTest {

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