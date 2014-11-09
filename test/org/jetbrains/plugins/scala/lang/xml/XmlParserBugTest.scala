package org.jetbrains.plugins.scala
package lang.xml

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter


/**
 * User: Dmitry Naydanov
 * Date: 3/3/12
 */

class XmlParserBugTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL1699() {
    val text =
      """
      | object Foo{
      |   val bar = <bar id="&amp;" />
      | }
      """.stripMargin.replace("\r", "")

    checkTextHasNoErrors(text)
  }

  def testSCL3388() {
    val text = "class A { val xml = <span>&rarr;</span> }"

    checkTextHasNoErrors(text)
  }

  def testSCL3299() {
    val text =
      """
      | object TestObject {
      |   val sampleVar = 1
      |   def getxml = <a><![CDATA[{sampleVar}]]></a>
      | }
      """.stripMargin.replace("\r", "")

    checkTextHasNoErrors(text)
  }
}
