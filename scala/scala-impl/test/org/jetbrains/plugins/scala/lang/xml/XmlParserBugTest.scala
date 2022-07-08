package org.jetbrains.plugins.scala
package lang.xml

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class XmlParserBugTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL1699(): Unit = {
    val text =
      """
      | object Foo{
      |   val bar = <bar id="&amp;" />
      | }
      """.stripMargin.replace("\r", "")

    checkTextHasNoErrors(text)
  }

  def testSCL3388(): Unit = {
    val text = "class A { val xml = <span>&rarr;</span> }"

    checkTextHasNoErrors(text)
  }

  def testSCL3299(): Unit = {
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
