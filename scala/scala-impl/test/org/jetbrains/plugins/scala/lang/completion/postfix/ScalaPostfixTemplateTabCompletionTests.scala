package org.jetbrains.plugins.scala
package lang.completion.postfix

import org.jetbrains.plugins.scala.base.{ScalaLightCodeInsightFixtureTestCase, SharedTestProjectToken}
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

@Category(Array(classOf[CompletionTests]))
abstract class ScalaPostfixTemplateTabCompletionTestBase extends ScalaLightCodeInsightFixtureTestCase {
  override def getTestDataPath = super.getTestDataPath + "postfixTemplate/tabCompletion"

  override protected def sharedProjectToken = SharedTestProjectToken.ByScalaSdkAndProjectLibraries(this)

  protected def doTest(testName: String = getTestName(true))(textToType: String = "." + testName): Unit = {
    myFixture.configureByFile(testName + ".scala")
    myFixture.`type`(textToType + "\t")
    myFixture.checkResultByFile(testName + "-after.scala", true)
  }
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest,
))
class ScalaPostfixTemplateTabCompletionTest extends ScalaPostfixTemplateTabCompletionTestBase {
  def testTry(): Unit = doTest()()

  def testAssert(): Unit = doTest()()

  def testCast(): Unit = doTest()()

  def testElse(): Unit = doTest()()

  def testIf(): Unit = doTest()()

  def testFor(): Unit = doTest()()

  def testField(): Unit = doTest()()

  def testVar(): Unit = doTest()()

  def testNot(): Unit = doTest()()

  def testNotBang(): Unit = doTest("not")("!")

  def testPar(): Unit = doTest()()

  def testReturn(): Unit = doTest()()

  def testSout(): Unit = doTest("println")(".sout")

  def testPrtln(): Unit = doTest("println")(".prtln")

  def testThrow(): Unit = doTest()()

  def testWhile(): Unit = doTest()()

  def testDoWhile(): Unit = doTest()(".dowhile")

  def testIsNull(): Unit = doTest()(".null")

  def testNotNull(): Unit = doTest()(".notnull")

  def testNotNullNn(): Unit = doTest("notNull")(".nn")

  def testOption(): Unit = doTest()(".Option")

  def testSeq(): Unit = doTest()(".Seq")

  def testList(): Unit = doTest()(".List")
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_2_13))
class ScalaPostfixTemplateTabCompletionTest_2_13 extends ScalaPostfixTemplateTabCompletionTestBase {
  def testMatch(): Unit = doTest()()

  def testExhaustiveMatch(): Unit = doTest()(".match")
}

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(TestScalaVersion.Scala_3_Latest))
class ScalaPostfixTemplateTabCompletionTest_3_Latest extends ScalaPostfixTemplateTabCompletionTestBase {
  override def getTestDataPath = super.getTestDataPath + "/scala3"

  def testMatch(): Unit = doTest()()

  def testExhaustiveMatch(): Unit = doTest()(".match")
}
