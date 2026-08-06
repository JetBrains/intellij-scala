package org.jetbrains.plugins.scala.testingSupport.scalatest.base.generators

import org.jetbrains.plugins.scala.testingSupport.scalatest.base.ScalaTestTestCase

trait PropSpecGenerator extends ScalaTestTestCase {

  protected val propSpecClassName = "PropSpecTest"
  protected val propSpecFileName = propSpecClassName + ".scala"

  addSourceFile(propSpecFileName,
    s"""$ImportsForPropSpec
       |
       |class $propSpecClassName extends $PropSpecBase {
       |
       |  property("Single tests should run") {
       |    print("$TestOutputPrefix OK $TestOutputSuffix")
       |  }
       |
       |  property("other test should not run") {
       |    print("$TestOutputPrefix FAILED $TestOutputSuffix")
       |  }
       |
       |  property("tagged", PropSpecTag) {}
       |}
       |
       |object PropSpecTag extends Tag("MyTag")
       |""".stripMargin.trim()
  )
}
