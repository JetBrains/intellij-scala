package org.jetbrains.plugins.scala
package lang.completion.postfix

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.impl.LiveTemplateCompletionContributor
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateLookupElement
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.openapi.util.Condition
import com.intellij.testFramework.UsefulTestCase.{assertNotEmpty, assertSize}
import com.intellij.util.containers.ContainerUtil
import junit.framework.TestCase.{assertNotNull, assertNull}
import org.jetbrains.plugins.scala.base.{ScalaCompletionAutoPopupTestCase, SharedTestProjectToken}
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.{ScalaExhaustiveMatchPostfixTemplate, ScalaMatchPostfixTemplate}
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

@Category(Array(classOf[CompletionTests]))
abstract class ScalaPostfixTemplateTabCompletionTestBase extends ScalaCompletionAutoPopupTestCase {
  private val tab = "\t"
  private val resultFilePostfix = "-after.scala"

  override def getTestDataPath = super.getTestDataPath + "postfixTemplate/tabCompletion"

  override protected def sharedProjectToken = SharedTestProjectToken.ByScalaSdkAndProjectLibraries(this)

  protected def doTestUniqueKeyTemplate(testName: String = getTestName(true))(textToType: String = "." + testName): Unit = {
    configureByFile(testName)
    myFixture.`type`(textToType + tab)
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

    myFixture.checkResultByFile(testName + resultFilePostfix, true)
  }
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
))
class ScalaPostfixTemplateTabCompletionTest extends ScalaPostfixTemplateTabCompletionTestBase {

  def testAssert(): Unit = doTestUniqueKeyTemplate()()

  def testCast(): Unit = doTestUniqueKeyTemplate()()

  def testFor(): Unit = doTestUniqueKeyTemplate()()

  def testField(): Unit = doTestUniqueKeyTemplate()()

  def testVar(): Unit = doTestUniqueKeyTemplate()()

  def testNot(): Unit = doTestUniqueKeyTemplate()()

  def testNotBang(): Unit = doTestUniqueKeyTemplate("not")("!")

  def testPar(): Unit = doTestUniqueKeyTemplate()()

  def testReturn(): Unit = doTestUniqueKeyTemplate()()

  def testSout(): Unit = doTestUniqueKeyTemplate("println")(".sout")

  def testPrtln(): Unit = doTestUniqueKeyTemplate("println")(".prtln")

  def testThrow(): Unit = doTestUniqueKeyTemplate()()

  def testWhile(): Unit = doTestUniqueKeyTemplate()()

  def testDoWhile(): Unit = doTestUniqueKeyTemplate()(".dowhile")

  def testIsNull(): Unit = doTestUniqueKeyTemplate()(".null")

  def testNotNull(): Unit = doTestUniqueKeyTemplate()(".notnull")

  def testNotNullNn(): Unit = doTestUniqueKeyTemplate("notNull")(".nn")

  def testOption(): Unit = doTestUniqueKeyTemplate()(".Option")

  def testSeq(): Unit = doTestUniqueKeyTemplate()(".Seq")

  def testList(): Unit = doTestUniqueKeyTemplate()(".List")
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
class ScalaPostfixTemplateTabCompletionTest_2_13 extends ScalaPostfixTemplateTabCompletionTestBase {
  def testMatch(): Unit = doTest(classOf[ScalaMatchPostfixTemplate])()

  def testExhaustiveMatch(): Unit = doTest(classOf[ScalaExhaustiveMatchPostfixTemplate])(".match")

  def testTry(): Unit = doTestUniqueKeyTemplate()()

  def testElse(): Unit = doTestUniqueKeyTemplate()()

  def testIf(): Unit = doTestUniqueKeyTemplate()()
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class ScalaPostfixTemplateTabCompletionTest_3_Latest extends ScalaPostfixTemplateTabCompletionTestBase {
  override def getTestDataPath: String = super.getTestDataPath + "/scala3"

  def testMatch(): Unit = doTest(classOf[ScalaMatchPostfixTemplate])()

  def testExhaustiveMatch(): Unit = doTest(classOf[ScalaExhaustiveMatchPostfixTemplate])(".match")

  def testTry(): Unit = doTestUniqueKeyTemplate()()

  def testElse(): Unit = doTestUniqueKeyTemplate()()

  def testIf(): Unit = doTestUniqueKeyTemplate()()
}
