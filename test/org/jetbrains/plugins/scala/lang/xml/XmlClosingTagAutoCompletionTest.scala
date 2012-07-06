package org.jetbrains.plugins.scala
package lang.xml

import lang.completion3.ScalaLightCodeInsightFixtureTestAdapter

/**
 * User: Dmitry Naydanov
 * Date: 3/3/12
 */

class XmlClosingTagAutoCompletionTest extends ScalaLightCodeInsightFixtureTestAdapter {
  import ScalaLightCodeInsightFixtureTestAdapter.CARET_MARKER
  
  private def checkGeneratedTextGt(text: String,  assumedStub: String) {
    checkGeneratedTextAfterTyping(text, assumedStub, '>')
  }

  private def checkGeneratedTextSlash(text: String,  assumedStub: String) {
    checkGeneratedTextAfterTyping(text, assumedStub, '/')
  }

  def testSimpleTag() {
    val text = "class A { val xml1 = <aaa" + CARET_MARKER + " }"
    val assumedStub = "class A { val xml1 = <aaa></aaa> }"

    checkGeneratedTextGt(text, assumedStub)
  }

  def testSimpleEmptyTag() {
    val text = "class A { val xml = <aaa" + CARET_MARKER + " }"
    val assumedStub = "class A { val xml = <aaa/> }"

    checkGeneratedTextSlash(text, assumedStub)
  }

  def testComplicatedTag() {
    val text = "class A { val xml = <a>blah blah <blah/> <b" + CARET_MARKER + "</a> }"
    val assumedStub = "class A { val xml = <a>blah blah <blah/> <b></b></a> }"

    checkGeneratedTextGt(text, assumedStub)
  }

  def testComplicatedEmptyTag() {
    val text = "class A { val xml = <a>blah blah <blah/> <abc" + CARET_MARKER + "</a> }"
    val assumedStub = "class A { val xml = <a>blah blah <blah/> <abc/></a> }"

    checkGeneratedTextSlash(text, assumedStub)
  }

  def testTagWithParams() {
    val text = "class A { <a param1=\"blah blah\"" + CARET_MARKER + " }"
    val assumedStub = "class A { <a param1=\"blah blah\"></a> }"

    checkGeneratedTextGt(text, assumedStub)
  }

  def testEmptyTagWithParams() {
    val text = "class A { <a param1=\"blah blah\"" + CARET_MARKER + " }"
    val assumedStub = "class A { <a param1=\"blah blah\"/> }"

    checkGeneratedTextSlash(text, assumedStub)
  }

  def testBigXml() {
    val text =
      ("""
      |<lift:TD.list all_id="all_todos">
      |  <div id="all_todos">
      |    <div>Exclude done
      |        <todo:exclude/>
      |    </div>
      |    <ul>
      |      <todo:list>
      |       <tag""" + CARET_MARKER + """
      |        <li>
      |          <todo:check>
      |              <input type="checkbox"/>
      |          </todo:check>
      |          <todo:priority>
      |            <select>
      |              <option>1</option>
      |            </select>
      |          </todo:priority>
      |          <todo:desc>To Do</todo:desc>
      |        </li>
      |      </todo:list>
      |    </ul>
      |  </div>
      |</lift:TD.list>
      """).stripMargin.replace("\r", "")

    val assumedStub =
      """
      |<lift:TD.list all_id="all_todos">
      |  <div id="all_todos">
      |    <div>Exclude done
      |        <todo:exclude/>
      |    </div>
      |    <ul>
      |      <todo:list>
      |       <tag></tag>
      |        <li>
      |          <todo:check>
      |              <input type="checkbox"/>
      |          </todo:check>
      |          <todo:priority>
      |            <select>
      |              <option>1</option>
      |            </select>
      |          </todo:priority>
      |          <todo:desc>To Do</todo:desc>
      |        </li>
      |      </todo:list>
      |    </ul>
      |  </div>
      |</lift:TD.list>
      """.stripMargin.replace("\r", "")


    checkGeneratedTextGt(text, assumedStub)
  }

  def testXmlPattern1() {
    val text =
      ("""
      | xml match {
      |   case <aaa""" + CARET_MARKER + """
      |}
      """).stripMargin.replace("\r", "")

    val assumedStub =
      ("""
      | xml match {
      |   case <aaa></aaa>
      |}
      """).stripMargin.replace("\r", "")

    checkGeneratedTextGt(text, assumedStub)
  }

  def testXmlPaternWithEmptyTag1() {
    val text =
      ("""
      | xml match {
      |   case <aaa""" + CARET_MARKER + """
      |}
      """).stripMargin.replace("\r", "")

    val assumedStub =
      ("""
      | xml match {
      |   case <aaa/>
      |}
      """).stripMargin.replace("\r", "")

    checkGeneratedTextSlash(text, assumedStub)
  }

  def testXmlPattern2() {
    val text =
      ("""
      | xml match {
      |   case <a></a> =>
      |   case <aaa""" + CARET_MARKER + """
      | }
      """).stripMargin.replace("\r", "")

    val assumedStub =
      ("""
      | xml match {
      |   case <a></a> =>
      |   case <aaa></aaa>
      | }
      """).stripMargin.replace("\r", "")

    checkGeneratedTextGt(text, assumedStub)
  }

  def testXmlPatternWithEmpryTag2() {
    val text =
      ("""
      | xml match {
      |   case <a></a> =>
      |   case <aaa""" + CARET_MARKER + """
      | }
      """).stripMargin.replace("\r", "")

    val assumedStub =
      ("""
      | xml match {
      |   case <a></a> =>
      |   case <aaa/>
      | }
      """).stripMargin.replace("\r", "")

    checkGeneratedTextSlash(text, assumedStub)
  }

  def testSwallowGtAfterEmptyTagEnd() {
    val text = "class A { val xml = <aaa/>" + CARET_MARKER + " }"

    checkGeneratedTextGt(text, text)
  }

  def testSwallowGtAfterEmptyTagEndInXmlPattern() {
    val header = 
      """
        |val xml = <aaa attr="<aaa//>" />
        |xml match { 
        |   case <aaa/""".stripMargin.replace("\r", "") 
    
    val text = header + CARET_MARKER + ">  => 1  }"
    
    val stub = header + ">" + CARET_MARKER + "  => 1  }"

    checkGeneratedTextGt(text, stub)
  }
}
