package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.ScalaVersion

class Scala3ExtensionTypeInferenceTest extends TypeInferenceTestBase {

  override protected def supportedIn(version: ScalaVersion) = version.isScala3

  def testSCL23491(): Unit = doTest(
    s"""
       |import scala.annotation.targetName
       |
       |trait Initialize[A]
       |trait Task[A] extends Initialize[Task[A]]
       |
       |extension [A1](inline in: Initialize[A1])
       |  inline def value: A1 = ???
       |
       |extension [A1](inline in: Initialize[Task[A1]])
       |  @targetName("valueIA1")
       |  inline def value: A1 = ???
       |
       |def usage(): Unit = {
       |  val task: Task[String] = ???
       |  ${START}task.value${END}
       |}
       |//String
       |""".stripMargin
  )

  def testExtensionsFromGiven(): Unit = doTest(
    s"""
       |import scala.annotation.targetName
       |
       |trait Initialize[A]
       |trait Task[A] extends Initialize[Task[A]]
       |
       |trait Extensions:
       |  extension [A1](inline in: Initialize[A1])
       |    inline def value: A1 = ???
       |
       |  extension [A1](inline in: Initialize[Task[A1]])
       |    @targetName("valueIA1")
       |    inline def value: A1 = ???
       |
       |given Extensions = ???
       |
       |def usage(): Unit = {
       |  val task: Task[String] = ???
       |  ${START}task.value${END}
       |}
       |//String
       |""".stripMargin
  )
}
