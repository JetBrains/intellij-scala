package org.jetbrains.plugins.scala.codeInsight.template.macros

import org.junit.Assert.fail

trait DoTestInCompanionObject {
  self: ScalaLiveTemplateTestBase =>

  protected def doTestInCompanionObject(classText: String, expectedMethodText: String): Unit = {
    val regex = """.*?class\s+([\w\d]+).*""".r
    val className = classText.linesIterator.next() match {
      case regex(name) => name
      case _           => fail(s"couldn't extract class name from class:\n${classText}")
    }
    val before =
      s"""$classText
         |object $className {
         |  $CARET
         |}
         |""".stripMargin
    val after =
      s"""$classText
         |object $className {
         |  $expectedMethodText
         |}
         |""".stripMargin
    doTest(before, after)
  }
}
