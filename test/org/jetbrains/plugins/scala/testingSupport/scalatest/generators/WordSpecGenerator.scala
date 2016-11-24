package org.jetbrains.plugins.scala.testingSupport.scalatest.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.ScalaTestTestCase

/**
  * @author Roman.Shein
  * @since 10.02.2015.
  */
trait WordSpecGenerator extends ScalaTestTestCase {
  val wordSpecClassName = "WordSpecTest"

  val wordSpecFileName = wordSpecClassName + ".scala"

  addSourceFile("WordSpecTest.scala",
    """
      |import org.scalatest._
      |
      |class WordSpecTest extends WordSpec {
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
