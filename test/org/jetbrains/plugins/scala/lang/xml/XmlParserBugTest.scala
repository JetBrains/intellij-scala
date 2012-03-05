package org.jetbrains.plugins.scala
package lang.xml

import com.intellij.codeInsight.folding.CodeFoldingManager
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase


/**
 * User: Dmitry Naydanov
 * Date: 3/3/12
 */

class XmlParserBugTest extends LightCodeInsightFixtureTestCase {
  private def checkErrors(text: String) {
    myFixture.configureByText("dummy.scala", text)
    CodeFoldingManager.getInstance(getProject).buildInitialFoldings(myFixture.getEditor)

    myFixture.testHighlighting(false, false, false, myFixture.getFile.getVirtualFile)
  }

  def testSCL1699() {
    val text =
      """
      | object Foo{
      |   val bar = <bar id="&amp;" />
      | }
      """.stripMargin.replace("\r", "")

    checkErrors(text)
  }

  def testSCL3388() {
    val text = "class A { val xml = <span>&rarr;</span> }"

    checkErrors(text)
  }

  def testSCL3299() {
    val text =
      """
      | object TestObject {
      |   val sampleVar = 1
      |   def getxml = <a><![CDATA[{sampleVar}]]></a>
      | }
      """.stripMargin.replace("\r", "")

    checkErrors(text)
  }
}
