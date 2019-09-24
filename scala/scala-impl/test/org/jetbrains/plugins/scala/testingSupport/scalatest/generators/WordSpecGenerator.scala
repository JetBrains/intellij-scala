package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

trait WordSpecGenerator extends ScalaTestTestCase {

  val wordSpecClassName = "WordSpecTest"

  val wordSpecFileName: String = wordSpecClassName + ".scala"

  addSourceFile(wordSpecFileName,
    s"""
       |import org.scalatest._
       |
       |class $wordSpecClassName extends WordSpec {
       |  "WordSpecTest" should {
       |    "Run single test" in {
       |      print(">>TEST: OK<<")
       |    }
       |
       |    "ignore other tests" in {
       |      print(">>TEST: FAILED<<")
       |    }
       |  }
       |
       |  "empty" should {}
       |
       |  "outer" should {
       |    "inner" in {}
       |  }
       |
       |  "tagged" should {
       |    "be tagged" taggedAs(WordSpecTag) in {}
       |  }
       |}
       |
       |object WordSpecTag extends Tag("MyTag")
    """.stripMargin.trim()
  )
}
