package org.jetbrains.plugins.scala
package codeInspection
package xml

import com.intellij.codeInspection.LocalInspectionTool

abstract class XmlUnmatchedTagInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[ScalaXmlUnmatchedTagInspection]
}

class NoMatchingOpeningTagTest extends XmlUnmatchedTagInspectionTest {

  override protected val description: String =
    ScalaBundle.message("xml.no.opening.tag")

  def testSimpleClosingError(): Unit = {
    val text = s"val xml = <aaa>blah blah$START</aab>$END"
    checkTextHasError(text)
  }

  def testNestedClosingError(): Unit =
    checkTextHasError(
      s"""
        val xml = <aaa attr="1" attr2="2">
                    <bbb>blah blah</bbb>
                    <ccc attr="100500">
                      <ddd>
                        blah
                      $START</dde>$END
                    </ccc>
                  </aaa>
      """.stripMargin)

  def testErrorInsideCase(): Unit =
    checkTextHasError(
      s"""
        <a>blah</a> match {
          case <b>blah</b> =>
          case <aa><bb><cc>{e@_*}</cc>$START</dd>$END</aa> =>
          case _ =>
        }
      """.stripMargin)
}

class NoMatchingClosingTagTest extends XmlUnmatchedTagInspectionTest {

  override protected val description: String =
    ScalaBundle.message("xml.no.closing.tag")

  def testSimpleOpeningError(): Unit =
    checkTextHasError(s"val xml = $START" + "<sdgdsjh attr1=\"1\">" + s"${END}blah lbah</asfgsd>")

  def testNestedOpeningError(): Unit =
    checkTextHasError(
      s"""
        val xml = <aaa attr="1" attr2="2">
                    <bbb>blah blah</bbb>
                    <ccc attr="100500">
                      $START<ddd>$END
                        blah
                      </dde>
                    </ccc>
                  </aaa>
      """.stripMargin)
}
