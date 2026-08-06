package org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait WordSpecGenerator extends ScalaTestTestCase {

  protected val wordSpecClassName = "WordSpecTest"
  protected val wordSpecFileName: String = wordSpecClassName + ".scala"

  addSourceFile(wordSpecFileName,
    s"""$ImportsForWordSpec
       |
       |class $wordSpecClassName extends $WordSpecBase {
       |  "WordSpecTest" should {
       |    "Run single test" in {
       |      print("$TestOutputPrefix OK $TestOutputSuffix")
       |    }
       |
       |    "ignore other tests" in {
       |      print("$TestOutputPrefix FAILED $TestOutputSuffix")
       |    }
       |  }
       |
       |  "empty" should { () }
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
       |""".stripMargin
  )
}
