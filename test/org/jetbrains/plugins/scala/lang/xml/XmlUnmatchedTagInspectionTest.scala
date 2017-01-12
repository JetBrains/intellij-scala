package org.jetbrains.plugins.scala
package lang.xml

import com.intellij.testFramework.EditorTestUtil
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.codeInspection.xml.ScalaXmlUnmatchedTagInspection

/**
 * User: Dmitry Naydanov
 * Date: 4/9/12
 */
class XmlUnmatchedTagInspectionTest extends ScalaLightCodeInsightFixtureTestAdapter {

  import EditorTestUtil.{SELECTION_END_TAG => END, SELECTION_START_TAG => START}

  val noOpeningTagError = ScalaBundle.message("xml.no.opening.tag")
  val noClosingTagError = ScalaBundle.message("xml.no.closing.tag")

  private def check(text: String, annotation: String) {
    checkTextHasError(text, annotation, classOf[ScalaXmlUnmatchedTagInspection])
  }


  def testSimpleClosingError() {
    val text = "val xml = <aaa>blah blah" + START + "</aab>" + END

    check(text, noOpeningTagError)
  }

  def testSimpleOpeningError() {
    val text = "val xml = " + START + "<sdgdsjh attr1=\"1\">" + END + "blah lbah</asfgsd>"

    check(text, noClosingTagError)
  }

  def testNestedOpeningError() {
    val text =
      """
        val xml = <aaa attr="1" attr2="2">
                    <bbb>blah blah</bbb>
                    <ccc attr="100500">
                      """ + START +
        """<ddd>""" + END +
        """
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
                      """ + START +
        """</dde>""" + END +
        """
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
          case <aa><bb><cc>{e@_*}</cc>""" + START +
        """</dd>""" + END +
        """</aa> =>
          case _ =>
        }
      """

    check(text, noOpeningTagError)
  }
}
