package org.jetbrains.plugins.scala
package lang.xml

import com.intellij.codeInspection.LocalInspectionTool
import codeInspection.xml.ScalaXmlUnmatchedTagInspection
import collection.mutable.ListBuffer
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.CommandProcessor
import org.jetbrains.plugins.scala.extensions
import base.ScalaLightCodeInsightFixtureTestAdapter

/**
 * User: Dmitry Naydanov
 * Date: 4/13/12
 */

class XmlUnmatchedTagQuickFixesTest extends ScalaLightCodeInsightFixtureTestAdapter {
  val renameOpeningQuickFixHint = ScalaBundle.message("xml.rename.opening.tag")
  val renameClosingQuickFixHint = ScalaBundle.message("xml.rename.closing.tag")
  val deleteUnmatchedTagHint = ScalaBundle.message("xml.delete.unmatched.tag")

  private def check(text: String, assumedStub: String, hint: String) {
    testQuickFix(text.replace("\r", ""), assumedStub.replace("\r", ""), hint, classOf[ScalaXmlUnmatchedTagInspection])
  }

  def testSimple() {
    val text = "val xml = <a>blah</b>"
    val assumedStub = "val xml = <a>blah</a>"

    check(text, assumedStub, renameClosingQuickFixHint)
  }

  def testWithAttributes() {
    val text = """val xml = <aaa attr1="1" attr2="attr2">blah blah</bbb>"""
    val assumedStub = """val xml = <bbb attr1="1" attr2="attr2">blah blah</bbb>"""

    check(text, assumedStub, renameOpeningQuickFixHint)
  }

  def testNested() {
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

    check(text, assumedStub, renameOpeningQuickFixHint)
  }

  def testInsideCase() {
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

    check(text, assumedStub, renameClosingQuickFixHint)
  }
}
