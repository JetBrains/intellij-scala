package org.jetbrains.plugins.scala
package lang.xml

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.codeInspection.xml.ScalaXmlUnmatchedTagInspection

/**
 * User: Dmitry Naydanov
 * Date: 4/9/12
 */

class XmlUnmatchedTagInspectionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  val noOpeningTagError = ScalaBundle.message("xml.no.opening.tag")
  val noClosingTagError = ScalaBundle.message("xml.no.closing.tag")
  val s = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_START
  val e = ScalaLightCodeInsightFixtureTestAdapter.SELECTION_END


  private def check(text: String, annotation: String) {
    checkTextHasError(text, annotation, classOf[ScalaXmlUnmatchedTagInspection])
  }


  def testSimpleClosingError() {
    val text = "val xml = <aaa>blah blah" + s + "</aab>" + e

    check(text, noOpeningTagError)
  }

  def testSimpleOpeningError() {
    val text = "val xml = " + s + "<sdgdsjh attr1=\"1\">" + e + "blah lbah</asfgsd>"

    check(text, noClosingTagError)
  }

  def testNestedOpeningError() {
    val text =
      """
        val xml = <aaa attr="1" attr2="2">
                    <bbb>blah blah</bbb>
                    <ccc attr="100500">
                      """ + s + """<ddd>""" + e + """
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
                      """ + s + """</dde>""" + e + """
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
          case <aa><bb><cc>{e@_*}</cc>""" + s + """</dd>""" + e + """</aa> =>
          case _ =>
        }
      """

    check(text, noOpeningTagError)
  }
}
