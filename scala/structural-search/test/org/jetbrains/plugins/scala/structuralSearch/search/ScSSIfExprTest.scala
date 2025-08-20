package org.jetbrains.plugins.scala.structuralSearch.search

import org.jetbrains.plugins.scala.structuralSearch.ScalaStructuralSearchTestCase

class ScSSIfExprTest extends ScalaStructuralSearchTestCase {

  def testDefault(): Unit = {
    val content =
      """if (a) b
        |else c
        |"""
    val pattern = content

    findAndMatch(
      "Dummy If 1",
      content,
      content,
      Seq(content)
    )
  }
}
