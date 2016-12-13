package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.completion.CompletionType

/**
  * Created by Kate Ustyuzhanina on 11/25/16.
  */
class ScalaExpectedTypeTest extends ScalaCompletionSortingTestCase(CompletionType.BASIC, "/completion3/expectedType/") {
  def testFuncWithParam(): Unit = {
    checkPreferredItems(0, "kurumba", "karamba")
  }

  def testStaticMethod(): Unit = {
    checkPreferredItems(0, "foo", "faa")
  }

  def testAfterNew(): Unit = {
    checkPreferredItems(0, "File")
  }

  def testStaticMethodParam(): Unit ={
    checkPreferredItems(0, "int")
  }
}