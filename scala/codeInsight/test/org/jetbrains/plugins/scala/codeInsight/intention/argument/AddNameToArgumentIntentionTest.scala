package org.jetbrains.plugins.scala
package codeInsight
package intention
package argument

import com.intellij.testFramework.EditorTestUtil

/**
  * @author Ksenia.Sautina
  * @since 5/14/12
  */
class AddNameToArgumentIntentionTest extends intentions.ScalaIntentionTestBase {

  import EditorTestUtil.{CARET_TAG => CARET}

  override def familyName = AddNameToArgumentIntention.FamilyName

  def test1(): Unit = {
    val text =
      s"""
         |class NameParameters {
         |  def doSomething(flag: Boolean) {}
         |
         |  doSomething(t${CARET}rue)
         |}
      """.stripMargin
    val resultText =
      s"""
         |class NameParameters {
         |  def doSomething(flag: Boolean) {}
         |
         |  doSomething(flag = t${CARET}rue)
         |}
      """.stripMargin

    doTest(text, resultText)
  }

  def test2(): Unit = {
    val text =
      s"""
         |class NameParameters {
         |  def doSomething(flag: Boolean, a: Int) {}
         |
         |  doSomething(t${CARET}rue, 8)
         |}
      """.stripMargin
    val resultText =
      s"""
         |class NameParameters {
         |  def doSomething(flag: Boolean, a: Int) {}
         |
         |  doSomething(flag = t${CARET}rue, a = 8)
         |}
      """.stripMargin

    doTest(text, resultText)
  }

  def test3(): Unit = {
    val text =
      s"""
         |class NameParameters {
         |  def doSomething(flag: Boolean, a: Int, b: Int) {}
         |
         |  doSomething(true, 8, ${CARET}9)
         |}
      """.stripMargin
    val resultText =
      s"""
         |class NameParameters {
         |  def doSomething(flag: Boolean, a: Int, b: Int) {}
         |
         |  doSomething(true, 8, ${CARET}b = 9)
         |}
      """.stripMargin

    doTest(text, resultText)
  }

}