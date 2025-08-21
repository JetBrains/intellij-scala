package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase

class ScSSIfExprTest extends ScalaStructuralSearchTestCase {

//  def testDefault(): Unit = {
//    val content =
//      """if (a) b
//        |else c
//        |"""
//    val pattern = content
//
//    findAndMatch(
//      "Dummy If 1",
//      content,
//      content,
//      Seq(content)
//    )
//  }

  def testNested(): Unit = {
    val content =
      """<match="AB">if (a) b
        |else {
        |  <match="AA">if (c) d
        |  else e</match="AA">
        |}</match="AB">
        |"""
    val pattern =
      """if ($a$) $b$
        |else $c$
        |"""

    matchAndAssert(
      "Nested ifs with vars",
      content,
      pattern
    )

//    findAndMatch(
//      "Dummy If 1",
//      content,
//      pattern,
//      Seq(content)
//    )
  }
}
