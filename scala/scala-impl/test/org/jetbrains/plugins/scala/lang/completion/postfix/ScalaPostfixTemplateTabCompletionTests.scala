package org.jetbrains.plugins.scala
package lang.completion.postfix

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateLookupElement
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.util.Condition
import com.intellij.testFramework.UsefulTestCase.{assertNotEmpty, assertSize}
import com.intellij.testFramework.{EdtTestUtil, NeedsIndex}
import com.intellij.util.containers.ContainerUtil
import junit.framework.TestCase.{assertNotNull, assertNull, fail}
import org.jetbrains.plugins.scala.base.ScalaCompletionAutoPopupTestCase
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.{ScalaExhaustiveMatchPostfixTemplate, ScalaMatchPostfixTemplate}
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestFixture.lookupItemsDebugText
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithAllIndexingModes, RunWithScalaVersions, TestScalaVersion}
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import scala.jdk.CollectionConverters.CollectionHasAsScala

@Category(Array(classOf[CompletionTests]))
abstract class ScalaPostfixTemplateTabCompletionTestBase extends ScalaCompletionAutoPopupTestCase {
  private val tab = "\t"
  private val resultFilePostfix = "-after.scala"

  override def getTestDataPath = super.getTestDataPath + "postfixTemplate/tabCompletion"

  protected def doTestUniqueKeyTemplate(testName: String = getTestName(true))(textToType: String = "." + testName): Unit = {
    configureByFile(testName)
    myFixture.`type`(textToType + tab)
    EdtTestUtil.runInEdtAndWait(() => NonBlockingReadActionImpl.waitForAsyncTaskCompletion())
    myFixture.checkResultByFile(testName + resultFilePostfix, true)
  }

  def doTest(expectedTemplateClass: Class[_ <: PostfixTemplate], testName: String = getTestName(true))
            (textToType: String = "." + testName): Unit = {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable)

    configureByFile(testName)
    doType(textToType)

    val lookup = getLookup
    assertNotNull(lookup)

    val items = lookup.getItems
    assertNotEmpty(items)

    val templateItemCondition: Condition[LookupElement] = {
      case lookupItem: PostfixTemplateLookupElement =>
        expectedTemplateClass.isInstance(lookupItem.getPostfixTemplate)
      case _ => false
    }
    val itemsOfExpectedType = ContainerUtil.findAll(items, templateItemCondition)
    assertSize(1, itemsOfExpectedType)

    lookup.setCurrentItem(itemsOfExpectedType.get(0))
    doType(tab)
    assertNull(getLookup)

    EdtTestUtil.runInEdtAndWait(() => NonBlockingReadActionImpl.waitForAsyncTaskCompletion())
    myFixture.checkResultByFile(testName + resultFilePostfix, true)
  }
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithAllIndexingModes
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
))
class ScalaPostfixTemplateTabCompletionTest extends ScalaPostfixTemplateTabCompletionTestBase {

  @NeedsIndex.SmartMode(reason = "`assert` needs type inference to check conformance with Boolean")
  def testAssert(): Unit = doTestUniqueKeyTemplate()()

  def testCast(): Unit = doTestUniqueKeyTemplate()()

  @NeedsIndex.SmartMode(reason = "`for` needs type inference to check sameOrInheritor")
  def testFor(): Unit = doTestUniqueKeyTemplate()()

  def testField(): Unit = doTestUniqueKeyTemplate()()

  def testVar(): Unit = doTestUniqueKeyTemplate()()

  @NeedsIndex.SmartMode(reason = "`not` needs type inference to check conformance with Boolean")
  def testNot(): Unit = doTestUniqueKeyTemplate()()

  @NeedsIndex.SmartMode(reason = "`!` needs type inference to check conformance with Boolean")
  def testNotBang(): Unit = doTestUniqueKeyTemplate("not")("!")

  def testPar(): Unit = doTestUniqueKeyTemplate()()

  def testReturn(): Unit = doTestUniqueKeyTemplate()()

  def testSout(): Unit = doTestUniqueKeyTemplate("println")(".sout")

  def testPrtln(): Unit = doTestUniqueKeyTemplate("println")(".prtln")

  @NeedsIndex.SmartMode(reason = "`throw` needs type inference to check sameOrInheritor")
  def testThrow(): Unit = doTestUniqueKeyTemplate()()

  @NeedsIndex.SmartMode(reason = "`while` needs type inference to check conformance with Boolean")
  def testWhile(): Unit = doTestUniqueKeyTemplate()()

  @NeedsIndex.SmartMode(reason = "`do-while` needs type inference to check conformance with Boolean")
  def testDoWhile(): Unit = doTestUniqueKeyTemplate()(".dowhile")

  @NeedsIndex.SmartMode(reason = "`null` needs type inference to check conformance with AnyRef")
  def testIsNull(): Unit = doTestUniqueKeyTemplate()(".null")

  @NeedsIndex.SmartMode(reason = "`notnull` needs type inference to check conformance with AnyRef")
  def testNotNull(): Unit = doTestUniqueKeyTemplate()(".notnull")

  @NeedsIndex.SmartMode(reason = "`nn` needs type inference to check conformance with AnyRef")
  def testNotNullNn(): Unit = doTestUniqueKeyTemplate("notNull")(".nn")

  def testOption(): Unit = doTestUniqueKeyTemplate()(".Option")

  def testSeq(): Unit = doTestUniqueKeyTemplate()(".Seq")

  def testList(): Unit = doTestUniqueKeyTemplate()(".List")

  def testNothingInComment(): Unit = {
    LiveTemplateCompletionContributor.setShowTemplatesInTests(true, myFixture.getTestRootDisposable)

    configureByFile(getTestName(true))
    doType(".")

    val lookup = getLookup
    if (lookup != null) {
      fail(
        s"""No lookup expected.
           |All lookup items:
           |${lookupItemsDebugText(lookup.getItems.asScala)}""".stripMargin)
    }
  }
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithAllIndexingModes
@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
class ScalaPostfixTemplateTabCompletionTest_2_13 extends ScalaPostfixTemplateTabCompletionTestBase {
  def testMatch(): Unit = doTest(classOf[ScalaMatchPostfixTemplate])()

  @NeedsIndex.SmartMode(reason = "exhaustive match needs type inference")
  def testExhaustiveMatch(): Unit = doTest(classOf[ScalaExhaustiveMatchPostfixTemplate])(".match")

  def testTry(): Unit = doTestUniqueKeyTemplate()()

  def testElse(): Unit = doTestUniqueKeyTemplate()()

  def testIf(): Unit = doTestUniqueKeyTemplate()()
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithAllIndexingModes
@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class ScalaPostfixTemplateTabCompletionTest_3_Latest extends ScalaPostfixTemplateTabCompletionTestBase {
  override def getTestDataPath: String = super.getTestDataPath + "/scala3"

  def testMatch(): Unit = doTest(classOf[ScalaMatchPostfixTemplate])()

  @NeedsIndex.SmartMode(reason = "exhaustive match needs type inference")
  def testExhaustiveMatch(): Unit = doTest(classOf[ScalaExhaustiveMatchPostfixTemplate])(".match")

  def testTry(): Unit = doTestUniqueKeyTemplate()()

  def testElse(): Unit = doTestUniqueKeyTemplate()()

  def testIf(): Unit = doTestUniqueKeyTemplate()()
}
