package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType
import org.jetbrains.plugins.scala.codeInsight.ScalaCodeInsightTestBase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.refactoring.util.TypeAnnotationSettings

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
      |  var intVariable = 43
      |  type A
      |  def abstractFoo
      |
      |  @throws(classOf[Exception])
      |  def annotFoo(int: Int): Int = 45
      |}
    """
  
  private def handleText(text: String): String = text.stripMargin.replaceAll("\r", "").trim()
  
  def testFunction() {
    TypeAnnotationSettings.set(getProjectAdapter,
      TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter)))
    
    val inText =
      """
        |class Inheritor extends Base {
        |   override def f<caret>
        |}
      """
    
    configureFromFileTextAdapter("dummy.scala", handleText(baseText + inText))
    
    val outText =
      """
        |class Inheritor extends Base {
        |  override def foo(int: Int): Int = super.foo(int)
        |}
      """
    
    complete(1, CompletionType.BASIC)
    completeLookupItem()
    checkResultByText(handleText(baseText + outText))
  }
  
  def testValue() {
    TypeAnnotationSettings.set(getProjectAdapter,
      TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter)))
    val inText =
      """
        |class Inheritor extends Base {
        |   override val intVa<caret>
        |}
      """
    configureFromFileTextAdapter("dummy.scala", handleText(baseText + inText))
    val (activeLookup, _) = complete(1, CompletionType.BASIC)
    
    val outText =
      """
        |class Inheritor extends Base {
        |  override val intValue: Int = _
        |}
      """
    
    completeLookupItem(activeLookup.find(le => le.getLookupString.contains("intValue")).get, '\t')
    checkResultByText(handleText(baseText + outText))
  }
  
  def testVariable() {
    TypeAnnotationSettings.set(getProjectAdapter,
      TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter)))
    val inText =
      """
        |class Inheritor extends Base {
        |   override var i<caret>
        |}
      """
    configureFromFileTextAdapter("dummy.scala", handleText(baseText + inText))
    val (activeLookup, _) = complete(1, CompletionType.BASIC)
    
    val outText =
      """
        |class Inheritor extends Base {
        |  override var intVariable: Int = _
        |}
      """
    
    completeLookupItem(activeLookup.find(le => le.getLookupString.contains("intVariable")).get, '\t')
    checkResultByText(handleText(baseText + outText))
  }
  
  def testJavaObjectMethod() {
    TypeAnnotationSettings.set(getProjectAdapter,
      TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter)))
    
    val inText =
      """
        |class Inheritor extends Base {
        |   override def h<caret>
        |}
      """
    configureFromFileTextAdapter("dummy.scala", handleText(baseText + inText))
    val (activeLookup, _) = complete(1, CompletionType.BASIC)
    
    val outText =
      """
        |class Inheritor extends Base {
        |  override def hashCode(): Int = super.hashCode()
        |}
      """
    
    completeLookupItem(activeLookup.find(le => le.getLookupString.contains("hashCode")).get, '\t')
    checkResultByText(handleText(baseText + outText))
  }
  
  def testOverrideKeword() {
    TypeAnnotationSettings.set(getProjectAdapter,
      TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter)))
    
    val inText =
      """
        |class Inheritor extends Base {
        |   over<caret>
        |}
      """
    configureFromFileTextAdapter("dummy.scala", handleText(baseText + inText))
    val (activeLookup, _) = complete(1, CompletionType.BASIC)
    
    val outText =
      """
        |class Inheritor extends Base {
        |  override protected def foo(int: Int): Int = super.foo(int)
        |}
      """
    
    completeLookupItem(activeLookup.find(le => le.getLookupString.contains("foo")).get, '\t')
    checkResultByText(handleText(baseText + outText))
  }
  
  def testAbstractType(): Unit = {
    TypeAnnotationSettings.set(getProjectAdapter,
      TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter)))
    
    val inText =
      """
        |class Inheritor extends Base {
        |   override type <caret>
        |}
      """
    configureFromFileTextAdapter("dummy.scala", handleText(baseText + inText))
    val (activeLookup, _) = complete(1, CompletionType.BASIC)
    
    val outText =
      """
        |class Inheritor extends Base {
        |  override type A = this.type
        |}
      """
    
    completeLookupItem(activeLookup.find(le => le.getLookupString.contains("A")).get, '\t')
    checkResultByText(handleText(baseText + outText))
  }
  
  def testAbstractFucntion(): Unit = {
    TypeAnnotationSettings.set(getProjectAdapter,
      TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter)))
    
    val inText =
      """
        |class Inheritor extends Base {
        |   override protected def <caret>
        |}
      """
    configureFromFileTextAdapter("dummy.scala", handleText(baseText + inText))
    val (activeLookup, _) = complete(1, CompletionType.BASIC)
    
    val outText =
      """
        |class Inheritor extends Base {
        |  override protected def abstractFoo: Unit = ???
        |}
      """
    
    completeLookupItem(activeLookup.find(le => le.getLookupString.contains("abstractFoo")).get, '\t')
    checkResultByText(handleText(baseText + outText))
  }
  
  def testAllowOverrideFunctionWithoutOverrideKeyword(): Unit = {
    TypeAnnotationSettings.set(getProjectAdapter,
      TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter)))
    
    val inText =
      """
        |class Inheritor extends Base {
        |   protected def a<caret>
        |}
      """
    configureFromFileTextAdapter("dummy.scala", handleText(baseText + inText))
    val (activeLookup, _) = complete(1, CompletionType.BASIC)
    
    val outText =
      """
        |class Inheritor extends Base {
        |  override protected def abstractFoo: Unit = ???
        |}
      """
    
    completeLookupItem(activeLookup.find(le => le.getLookupString.contains("abstractFoo")).get, '\t')
    checkResultByText(handleText(baseText + outText))
  }
  
  def testAllowOverrideVariableWithoutOverrideKeyword(): Unit = {
    TypeAnnotationSettings.set(getProjectAdapter,
      TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter)))
    
    val inText =
      """
        |class Inheritor extends Base {
        |   var i<caret>
        |}
      """
    configureFromFileTextAdapter("dummy.scala", handleText(baseText + inText))
    val (activeLookup, _) = complete(1, CompletionType.BASIC)
    
    val outText =
      """
        |class Inheritor extends Base {
        |  override var intVariable: Int = _
        |}
      """
    
    completeLookupItem(activeLookup.find(le => le.getLookupString.contains("intVariable")).get, '\t')
    checkResultByText(handleText(baseText + outText))
  }
  
  def testNoCompletionInClassParameter(): Unit = {
    TypeAnnotationSettings.set(getProjectAdapter,
      TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter)))
    
    val inText =
      """
        |class Inheritor extends Base {
        |   class A(ab<caret>)
        |}
      """
    configureFromFileTextAdapter("dummy.scala", handleText(baseText + inText))
    val (activeLookup, _) = complete(1, CompletionType.BASIC)
    
    val result = activeLookup.find(le => le.getLookupString.contains("override") && le.getAllLookupStrings.contains("abstractFoo"))
    assert(result.isEmpty, "Override is not enable at this place")
  }
  
  def testNoCompletionAfterDot(): Unit = {
    TypeAnnotationSettings.set(getProjectAdapter,
      TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter)))
    
    val inText =
      """
        |class Inheritor extends Base {
        |   var i = 12.ab<caret>
        |}
      """
    configureFromFileTextAdapter("dummy.scala", handleText(baseText + inText))
    val (activeLookup, _) = complete(1, CompletionType.BASIC)
    
    val result = activeLookup.find(le => le.getLookupString.contains("override") && le.getAllLookupStrings.contains("abstractFoo"))
    assert(result.isEmpty, "Override is not enable at this place")
  }
  
  def testWithAnnotation(): Unit = {
    TypeAnnotationSettings.set(getProjectAdapter,
      TypeAnnotationSettings.alwaysAddType(ScalaCodeStyleSettings.getInstance(getProjectAdapter)))
    
    val inText =
      """
        |class Inheritor extends Base {
        |   annotFoo<caret>
        |}
      """
    configureFromFileTextAdapter("dummy.scala", handleText(baseText + inText))
    val (activeLookup, _) = complete(1, CompletionType.BASIC)
    
    val outText =
      """
        |class Inheritor extends Base {
        |  @throws(classOf[Exception])
        |  override def annotFoo(int: Int): Int = super.annotFoo(int)
        |}
      """
    
    completeLookupItem(activeLookup.find(le => le.getLookupString.contains("override") && le.getLookupString.contains("annotFoo")).get, '\t')
    checkResultByText(handleText(baseText + outText))
  }
}
