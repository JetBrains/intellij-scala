package org.jetbrains.plugins.scala.lang.resolve

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

class ApplyResolveTest extends SimpleResolveTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= LatestScalaVersions.Scala_3

  def testSCL24555(): Unit = {
    val scalaCode = (call: String) => {
      s"""
         |case class MyClass0 private(param1: String, param2: Int)
         |
         |case class MyClass1 private(param1: String, param2: Int, param3: Boolean = false)
         |
         |case class MyClass2 private(param1: String, param2: Int, param3: Boolean = false)
         |object MyClass2
         |
         |object Usage {
         |  ${call.init}$CARET${call.last}
         |}
         |""".stripMargin
    }

    val calls = Seq(
      "MyClass0(\"42\", 42)",
      "MyClass0(\"42\", 42, false)",
      "MyClass1(\"42\", 42)",
      "MyClass2(\"42\", 42)",
      "MyClass2(\"42\", 42, false)",
    )

    calls.foreach(call => checkHasErrorAroundCaret(scalaCode(call)))
  }
}
