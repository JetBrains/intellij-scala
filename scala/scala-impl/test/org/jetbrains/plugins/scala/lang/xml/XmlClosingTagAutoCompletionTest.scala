package org.jetbrains.plugins.scala
package lang.xml

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.base.EditorActionTestBase

class XmlClosingTagAutoCompletionTest extends EditorActionTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  private def checkGeneratedTextGt(text: String, assumedStub: String): Unit = {
    checkGeneratedTextAfterTyping(text, assumedStub, '>')
  }

  private def checkGeneratedTextSlash(text: String, assumedStub: String): Unit = {
    checkGeneratedTextAfterTyping(text, assumedStub, '/')
  }

  def testSimpleTag(): Unit = {
    val text = "class A { val xml1 = <aaa" + CARET_MARKER + " }"
    val assumedStub = "class A { val xml1 = <aaa></aaa> }"

    checkGeneratedTextGt(text, assumedStub)
  }

  def testSimpleEmptyTag(): Unit = {
    val text = "class A { val xml = <aaa" + CARET_MARKER + " }"
    val assumedStub = "class A { val xml = <aaa/> }"

    checkGeneratedTextSlash(text, assumedStub)
  }

  def testComplicatedTag(): Unit = {
    val text = "class A { val xml = <a>blah blah <blah/> <b" + CARET_MARKER + "</a> }"
    val assumedStub = "class A { val xml = <a>blah blah <blah/> <b></b></a> }"

    checkGeneratedTextGt(text, assumedStub)
  }

  def testComplicatedEmptyTag(): Unit = {
    val text = "class A { val xml = <a>blah blah <blah/> <abc" + CARET_MARKER + "</a> }"
    val assumedStub = "class A { val xml = <a>blah blah <blah/> <abc/></a> }"

    checkGeneratedTextSlash(text, assumedStub)
  }

  def testTagWithParams(): Unit = {
    val text = "class A { <a param1=\"blah blah\"" + CARET_MARKER + " }"
    val assumedStub = "class A { <a param1=\"blah blah\"></a> }"

    checkGeneratedTextGt(text, assumedStub)
  }

  def testEmptyTagWithParams(): Unit = {
    val text = "class A { <a param1=\"blah blah\"" + CARET_MARKER + " }"
    val assumedStub = "class A { <a param1=\"blah blah\"/> }"

    checkGeneratedTextSlash(text, assumedStub)
  }

  def testBigXml(): Unit = {
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

  def testXmlPattern1(): Unit = {
    val text =
      ("""
      | xml match {
      |   case <aaa""" + CARET_MARKER + """
      |}
      """).stripMargin.replace("\r", "")

    val assumedStub =
      """
        | xml match {
        |   case <aaa></aaa>
        |}
      """.stripMargin.replace("\r", "")

    checkGeneratedTextGt(text, assumedStub)
  }

  def testXmlPatternWithEmptyTag1(): Unit = {
    val text =
      ("""
      | xml match {
      |   case <aaa""" + CARET_MARKER + """
      |}
      """).stripMargin.replace("\r", "")

    val assumedStub =
      """
        | xml match {
        |   case <aaa/>
        |}
      """.stripMargin.replace("\r", "")

    checkGeneratedTextSlash(text, assumedStub)
  }

  def testXmlPattern2(): Unit = {
    val text =
      ("""
      | xml match {
      |   case <a></a> =>
      |   case <aaa""" + CARET_MARKER + """
      | }
      """).stripMargin.replace("\r", "")

    val assumedStub =
      """
        | xml match {
        |   case <a></a> =>
        |   case <aaa></aaa>
        | }
      """.stripMargin.replace("\r", "")

    checkGeneratedTextGt(text, assumedStub)
  }

  def testXmlPatternWithEmptyTag2(): Unit = {
    val text =
      ("""
      | xml match {
      |   case <a></a> =>
      |   case <aaa""" + CARET_MARKER + """
      | }
      """).stripMargin.replace("\r", "")

    val assumedStub =
      """
        | xml match {
        |   case <a></a> =>
        |   case <aaa/>
        | }
      """.stripMargin.replace("\r", "")

    checkGeneratedTextSlash(text, assumedStub)
  }

  def testSwallowGtAfterEmptyTagEnd(): Unit = {
    val text = "class A { val xml = <aaa/>" + CARET_MARKER + " }"

    checkGeneratedTextGt(text, text)
  }

  def testSwallowGtAfterEmptyTagEndInXmlPattern(): Unit = {
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
