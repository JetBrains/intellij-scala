package org.jetbrains.plugins.scala
package codeInspection
package xml

import com.intellij.codeInspection.LocalInspectionTool

abstract class XmlUnmatchedTagQuickFixesTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaXmlUnmatchedTagInspection]

  protected val hint: String
}

class RenameOpeningTagQuickFixTest extends XmlUnmatchedTagQuickFixesTest {

  override protected val description: String =
    ScalaBundle.message("xml.no.opening.tag")

  override protected val hint: String =
    ScalaBundle.message("xml.rename.opening.tag")

  def testWithAttributes(): Unit = {
    val text = """val xml = <aaa attr1="1" attr2="attr2">blah blah</bbb>"""
    val assumedStub = """val xml = <bbb attr1="1" attr2="attr2">blah blah</bbb>"""
    testQuickFix(text, assumedStub, hint)
  }

  def testNested(): Unit = {
    val text =
      """
        val xml = <aaa attr1="1">
                    <bbb>blah blah</bbb>
                    <ccc>
                      <bbb attrB="A" attrC="d">
                        {i + j + k}
                      </lll>
                    </ccc>
                  </aaa>
      """
    val assumedStub =
      """
        val xml = <aaa attr1="1">
                    <bbb>blah blah</bbb>
                    <ccc>
                      <lll attrB="A" attrC="d">
                        {i + j + k}
                      </lll>
                    </ccc>
                  </aaa>
      """
    testQuickFix(text, assumedStub, hint)
  }
}


class RenameClosingTagQuickFixTest extends XmlUnmatchedTagQuickFixesTest {

  override protected val description: String =
    ScalaBundle.message("xml.no.closing.tag")

  override protected val hint: String =
    ScalaBundle.message("xml.rename.closing.tag")

  def testSimple(): Unit = {
    val text = "val xml = <a>blah</b>"
    val assumedStub = "val xml = <a>blah</a>"
    testQuickFix(text, assumedStub, hint)
  }

  def testInsideCase(): Unit = {
    val text =
      """
        <aa></aa> match {
          case <aaa><bbb>{1 + 2 + i}</ccc></aaa> =>
          case _ =>
        }
      """
    val assumedStub =
      """
        <aa></aa> match {
          case <aaa><bbb>{1 + 2 + i}</bbb></aaa> =>
          case _ =>
        }
      """
    testQuickFix(text, assumedStub, hint)
  }
}
