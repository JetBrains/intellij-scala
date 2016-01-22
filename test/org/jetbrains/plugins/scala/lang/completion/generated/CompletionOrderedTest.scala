package org.jetbrains.plugins.scala.lang.completion.generated

import org.jetbrains.plugins.scala.lang.completion.CompletionTestBase

/**
  * Created by user
  * on 1/22/16
  */
class CompletionOrderedTest extends CompletionTestBase {
  override def folderPath: String = super.folderPath + "Ordered/"

  override def chechResult(got: Array[String], expected: String) {
    import _root_.junit.framework.Assert._
    val res = got.mkString("\n")
    assertEquals(expected, res.trim)
  }

  def testLocalBefore() = doTest() //expect localOrParam, membersCurrentClass(fields before methods), membersRest

  def testTypeExpected() = doTest() //expect localTypes, inClassTypes, rest sorted like usual
}