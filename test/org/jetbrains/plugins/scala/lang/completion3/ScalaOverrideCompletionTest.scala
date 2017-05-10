package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationSettings

/**
  * Created by kate
  * on 3/11/16
  */
class ScalaOverrideCompletionTest extends ScalaCodeInsightTestBase {

  private val baseText =
    """
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
    """

  def testFunction() {
    val inText =
      """
        |class Inheritor extends Base {
        |   override def f<caret>
        |}
      """

    val outText =
      """
        |class Inheritor extends Base {
        |  override def foo(int: Int): Int = super.foo(int)
        |}
      """

    doTest(baseText + inText, baseText + outText)
  }

  def testValue() {
    val inText =
      """
        |class Inheritor extends Base {
        |   override val intVa<caret>
        |}
      """
    val outText =
      """
        |class Inheritor extends Base {
        |  override val intValue: Int = _
        |}
      """
    doTest(baseText + inText, baseText + outText, Some("intValue"))
  }

  def testVariable() {
    val inText =
      """
        |class Inheritor extends Base {
        |   override var i<caret>
        |}
      """

    val outText =
      """
        |class Inheritor extends Base {
        |  override var intVariable: Int = _
        |}
      """
    doTest(baseText + inText, baseText + outText, Some("intVariable"))
  }

  def testJavaObjectMethod() {
    val inText =
      """
        |class Inheritor extends Base {
        |   override def h<caret>
        |}
      """

    val outText =
      """
        |class Inheritor extends Base {
        |  override def hashCode(): Int = super.hashCode()
        |}
      """

    doTest(baseText + inText, baseText + outText, Some("hashCode"))
  }

  def testOverrideKeword() {
    val inText =
      """
        |class Inheritor extends Base {
        |   over<caret>
        |}
      """

    val outText =
      """
        |class Inheritor extends Base {
        |  override protected def foo(int: Int): Int = super.foo(int)
        |}
      """

    doTest(baseText + inText, baseText + outText, Some("override def foo"))
  }

  def testAbstractType(): Unit = {
    val inText =
      """
        |class Inheritor extends Base {
        |   override type <caret>
        |}
      """

    val outText =
      """
        |class Inheritor extends Base {
        |  override type A = this.type
        |}
      """

    doTest(baseText + inText, baseText + outText, Some("A"))
  }

  def testAbstractFucntion(): Unit = {
    val inText =
      """
        |class Inheritor extends Base {
        |   override protected def <caret>
        |}
      """

    val outText =
      """
        |class Inheritor extends Base {
        |  override protected def abstractFoo: Unit = ???
        |}
      """

    doTest(baseText + inText, baseText + outText, Some("abstractFoo"))
  }

  def testAllowOverrideFunctionWithoutOverrideKeyword(): Unit = {
    val inText =
      """
        |class Inheritor extends Base {
        |   protected def a<caret>
        |}
      """

    val outText =
      """
        |class Inheritor extends Base {
        |  override protected def abstractFoo: Unit = ???
        |}
      """

    doTest(baseText + inText, baseText + outText, Some("abstractFoo"))
  }

  def testAllowOverrideVariableWithoutOverrideKeyword(): Unit = {
    val inText =
      """
        |class Inheritor extends Base {
        |   var i<caret>
        |}
      """

    val outText =
      """
        |class Inheritor extends Base {
        |  override var intVariable: Int = _
        |}
      """

    doTest(baseText + inText, baseText + outText, Some("intVariable"))
  }

  def testNoMethodCompletionInClassParameter(): Unit = {
    val inText =
      """
        |class Inheritor extends Base {
        |   class A(ab<caret>)
        |}
      """
    configureFromFileTextAdapter("dummy.scala", normalize(baseText + inText))
    val lookups = complete(1, CompletionType.BASIC)

    val result = lookups.find(le => le.getLookupString.contains("override") && le.getAllLookupStrings.contains("abstractFoo"))
    assert(result.isEmpty, "Override is not enable at this place")
  }

  def testNoCompletionAfterDot(): Unit = {
    val inText =
      """
        |class Inheritor extends Base {
        |   var i = 12.ab<caret>
        |}
      """
    configureFromFileTextAdapter("dummy.scala", normalize(baseText + inText))
    val lookups = complete(1, CompletionType.BASIC)

    val result = lookups.find(le => le.getLookupString.contains("override") && le.getAllLookupStrings.contains("abstractFoo"))
    assert(result.isEmpty, "Override is not enable at this place")
  }

  //Like in java, don't save annotations here
  def testWithAnnotation(): Unit = {
    val inText =
      """
        |class Inheritor extends Base {
        |   annotFoo<caret>
        |}
      """

    val outText =
      """
        |class Inheritor extends Base {
        |  override def annotFoo(int: Int): Int = super.annotFoo(int)
        |}
      """
    doTest(baseText + inText, baseText + outText, Some("override annotFoo"))
  }

  def testNoCompletionInFunciton(): Unit = {
    val inText =
      """
        |class Inheritor extends Base {
        |   def outherFunc(): Unit = {
        |     annotFoo<caret>
        |   }
        |}
      """
    configureFromFileTextAdapter("dummy.scala", normalize(baseText + inText))
    val lookups = complete(1, CompletionType.BASIC)

    val result = lookups.find(le => le.getLookupString.contains("override") && le.getAllLookupStrings.contains("abstractFoo"))
    assert(result.isEmpty, "Override is not enable at this place")
  }

  def testNoCompletionInModifier(): Unit = {
    val inText =
      """
        |class Inheritor extends Base {
        |   private[intV<caret>] val test = 123
        |}
      """
    configureFromFileTextAdapter("dummy.scala", normalize(baseText + inText))
    val lookups = complete(1, CompletionType.BASIC)

    val result = lookups.find(le => le.getLookupString.contains("override") && le.getAllLookupStrings.contains("intValue"))
    assert(result.isEmpty, "Override is not enable at this place")
  }

  def testNoCompletionAfterColon(): Unit = {
    val inText =
      """
        |class Inheritor extends Base {
        |   val test: intV<caret> = 123
        |}
      """
    configureFromFileTextAdapter("dummy.scala", normalize(baseText + inText))
    val lookups = complete(1, CompletionType.BASIC)

    val result = lookups.find(le => le.getLookupString.contains("override") && le.getAllLookupStrings.contains("intValue"))
    assert(result.isEmpty, "Override is not enable at this place")
  }

  def testParamsFromClass(): Unit = {
    val inText =
      """
        |class Person(val name: String) {
        |  val gender: Boolean = true
        |  val age: Int = 45
        |}
        |
        |case class ExamplePerson(override val nam<caret>, override val age: Int, override val gender: Boolean) extends Person("") {
        |}
      """

    val resultText =
      """
        |class Person(val name: String) {
        |  val gender: Boolean = true
        |  val age: Int = 45
        |}
        |
        |case class ExamplePerson(override val name: String, override val age: Int, override val gender: Boolean) extends Person("") {
        |}
      """

    doTest(inText, resultText)
  }

  def testParamsFromTrait(): Unit = {
    val inText = "class Test(ov<caret>) extends Base"
    val resultText = "class Test(override var intVariable: Int) extends Base"

    doTest(baseText + inText, baseText + resultText, Some("override intVariable"))
  }

  def doTest(inText: String, resultText: String, item: Option[String] = None): Unit = {
    TypeAnnotationSettings.set(getProjectAdapter,
      TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter)))

    configureFromFileTextAdapter("dummy.scala", normalize(inText))
    val lookups = complete(1, CompletionType.BASIC)

    assert(lookups.nonEmpty, "No lookup was found")

    item match {
      case Some(name) =>
        def containsAll(lookupString: String, parts: String): Boolean =
          parts.split(" ").forall(lookupString.contains)

        lookups.find(ls => containsAll(ls.getLookupString, name))
          .foreach(finishLookup(_))
      case _ => finishLookup()
    }
    checkResultByText(normalize(resultText))
  }
}
