package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EditorTestUtil.{CARET_TAG => CARET}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings

/**
  * Created by kate
  * on 3/11/16
  */
class ScalaOverrideCompletionTest extends ScalaCodeInsightTestBase {

  import ScalaCodeInsightTestBase._

  protected override def setUp(): Unit = {
    super.setUp()

    val project = getProject
    val codeStyleSettings = ScalaCodeStyleSettings.getInstance(project)

    import TypeAnnotationSettings.{alwaysAddType, set}
    set(project, alwaysAddType(codeStyleSettings))
  }

  def testFunction(): Unit = doCompletionTest(
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
      """,
    char = DEFAULT_CHAR,
    time = DEFAULT_TIME,
    completionType = DEFAULT_COMPLETION_TYPE
  ) {
    _ => true
  }

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
    item = "intValue"
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
    item = "intVariable"
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
    item = "hashCode"
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
    item = "override def foo"
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
    item = "A"
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
    item = "abstractFoo"
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
    item = "abstractFoo"
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
    item = "intVariable"
  )

  def testNoMethodCompletionInClassParameter(): Unit = checkNoCompletion(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   class A(ab$CARET)
         |}
      """,
    item = "abstractFoo"
  )

  def testNoCompletionAfterDot(): Unit = checkNoCompletion(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   var i = 12.ab$CARET
         |}
      """,
    item = "abstractFoo"
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
    item = "override annotFoo"
  )

  def testNoCompletionInFunction(): Unit = checkNoCompletion(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   def outherFunc(): Unit = {
         |     annotFoo$CARET
         |   }
         |}
      """,
    item = "abstractFoo"
  )

  def testNoCompletionInModifier(): Unit = checkNoCompletion(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   private[intV$CARET] val test = 123
         |}
      """,
    item = "intValue"
  )

  def testNoCompletionAfterColon(): Unit = checkNoCompletion(
    fileText =
      s"""
         |class Inheritor extends Base {
         |   val test: intV$CARET = 123
         |}
      """,
    item = "intValue"
  )

  def testParamsFromTrait(): Unit = doCompletionTest(
    fileText = s"class Test(ov$CARET) extends Base",
    resultText = "class Test(override var intVariable: Int) extends Base",
    item = "override intVariable"
  )

  override protected def checkNoCompletion(fileText: String,
                                           item: String,
                                           time: Int,
                                           completionType: CompletionType): Unit =
    super.checkNoCompletion(fileText, time, completionType) { lookup =>
      lookup.getLookupString.contains("override") &&
        lookup.getAllLookupStrings.contains(item)
    }

  override protected def doCompletionTest(fileText: String,
                                          resultText: String,
                                          item: String,
                                          char: Char,
                                          time: Int,
                                          completionType: CompletionType): Unit =
    super.doCompletionTest(fileText, resultText, char, time, completionType) { lookup =>
      val lookupString = lookup.getLookupString
      item.split(" ").forall(lookupString.contains)
    }

  import ScalaOverrideCompletionTest._

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
       |$fileText
    """
}

class ScalaOverrideCompletionTest2 extends ScalaCodeInsightTestBase {

  import ScalaCodeInsightTestBase._

  def testParamsFromClass(): Unit = doCompletionTest(
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
      """,
    char = DEFAULT_CHAR,
    time = DEFAULT_TIME,
    completionType = DEFAULT_COMPLETION_TYPE
  ) {
    _ => true
  }
}