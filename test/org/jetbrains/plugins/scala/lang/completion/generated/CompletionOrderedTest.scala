package org.jetbrains.plugins.scala.lang.completion.generated

import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.lang.completion.CompletionTestBase

/**
  * Created by kate
  * on 1/22/16
  */
class CompletionOrderedTest extends CompletionTestBase {
  override def folderPath: String = super.folderPath + "Ordered/"

  override def checkResult(got: Array[String], _expected: String) {
    val res = got.mkString("\n")
    UsefulTestCase.assertSameLines(_expected, res.trim)
  }

  def testLocalBefore() = doTest() //expect localOrParam, membersCurrentClass(fields before methods), membersRest

  def testTypeExpected() = doTest() //expect localTypes, inClassTypes, rest sorted like usual
}