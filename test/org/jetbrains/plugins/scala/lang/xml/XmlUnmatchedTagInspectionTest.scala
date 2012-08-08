package org.jetbrains.plugins.scala
package lang.xml

import lang.completion3.ScalaLightCodeInsightFixtureTestAdapter
import codeInspection.xml.ScalaXmlUnmatchedTagInspection
import com.intellij.codeInsight.CodeInsightTestCase
import com.intellij.codeInspection.LocalInspectionTool

/**
 * User: Dmitry Naydanov
 * Date: 4/9/12
 */

class XmlUnmatchedTagInspectionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  val noOpeningTagError = ScalaBundle.message("xml.no.opening.tag")
  val noClosingTagError = ScalaBundle.message("xml.no.closing.tag")
  import CodeInsightTestCase.{SELECTION_START_MARKER, SELECTION_END_MARKER}


  private def check(text: String, annotation: String) {
    checkTextHasError(text, annotation, classOf[ScalaXmlUnmatchedTagInspection])
  }


  def testSimpleClosingError() {
    val text = "val xml = <aaa>blah blah" + SELECTION_START_MARKER + "</aab>" + SELECTION_END_MARKER

    check(text, noOpeningTagError)
  }

  def testSimpleOpeningError() {
    val text = "val xml = " + SELECTION_START_MARKER + "<sdgdsjh attr1=\"1\">" + SELECTION_END_MARKER + "blah lbah</asfgsd>"

    check(text, noClosingTagError)
  }

  def testNestedOpeningError() {
    val text =
      """
        val xml = <aaa attr="1" attr2="2">
                    <bbb>blah blah</bbb>
                    <ccc attr="100500">
                      """ + SELECTION_START_MARKER + """<ddd>""" + SELECTION_END_MARKER + """
                        blah
                      </dde>
                    </ccc>
                  </aaa>
      """

    check(text, noClosingTagError)
  }

  def testNestedClosingError() {
    val text =
      """
        val xml = <aaa attr="1" attr2="2">
                    <bbb>blah blah</bbb>
                    <ccc attr="100500">
                      <ddd>
                        blah
                      """ + SELECTION_START_MARKER + """</dde>""" + SELECTION_END_MARKER + """
                    </ccc>
                  </aaa>
      """

    check(text, noOpeningTagError)
  }

  def testErrorInsideCase() {
    val text =
      """
        <a>blah</a> match {
          case <b>blah</b> =>
          case <aa><bb><cc>{e@_*}</cc>""" + SELECTION_START_MARKER + """</dd>""" + SELECTION_END_MARKER + """</aa> =>
          case _ =>
        }
      """

    check(text, noOpeningTagError)
  }
}
