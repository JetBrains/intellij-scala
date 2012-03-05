package org.jetbrains.plugins.scala
package lang.xml

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase

/**
 * User: Dmitry Naydanov
 * Date: 3/3/12
 */

class XmlClosingTagAutoCompletionTest extends LightPlatformCodeInsightFixtureTestCase {
  private def checkGeneratedText(text: String, assumedStub: String) {
    val caretIndex = text.indexOf("<caret>")
    myFixture.configureByText("dummy.scala", text.replace("<caret>", ""))
    myFixture.getEditor.getCaretModel.moveToOffset(caretIndex)

    myFixture.`type`('>')

    myFixture.checkResult(assumedStub)
  }

  def testSimpleTag() {
    val text = "class A { val xml1 = <aaa<caret> }"
    val assumedStub = "class A { val xml1 = <aaa></aaa> }"

    checkGeneratedText(text, assumedStub)
  }

  def testComplicatedTag() {
    val text = "class A { val xml = <a>blah blah <blah/> <b<caret></a> }"
    val assumedStub = "class A { val xml = <a>blah blah <blah/> <b></b></a> }"

    checkGeneratedText(text, assumedStub)
  }

  def testTagWithParams() {
    val text = "class A { <a param1=\"blah blah\"<caret> }"
    val assumedStub = "class A { <a param1=\"blah blah\"></a> }"

    checkGeneratedText(text, assumedStub)
  }

  def testBigXml() {
    val text =
      """
      |<lift:TD.list all_id="all_todos">
      |  <div id="all_todos">
      |    <div>Exclude done
      |        <todo:exclude/>
      |    </div>
      |    <ul>
      |      <todo:list>
      |       <tag<caret>
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


    checkGeneratedText(text, assumedStub)
  }
}
