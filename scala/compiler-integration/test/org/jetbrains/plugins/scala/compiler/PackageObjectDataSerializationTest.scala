package org.jetbrains.plugins.scala.compiler

import org.jetbrains.plugins.scala.compiler.CompilerMessagesUtil.assertNoErrorsOrWarnings
import org.jetbrains.plugins.scala.compiler.data.IncrementalityType
import org.jetbrains.plugins.scala.{ScalaVersion, SlowTests}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import org.junit.runners.JUnit4

import scala.jdk.CollectionConverters._

@Category(Array(classOf[SlowTests]))
@RunWith(classOf[JUnit4])
class PackageObjectDataSerializationTest extends ScalaCompilerTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_2_13

  override protected def incrementalityType: IncrementalityType = IncrementalityType.IDEA

  @Test
  def serializationSucceeds(): Unit = {
    addFileToProjectSources("org/example/MyTrait.scala",
      """package org.example
        |
        |trait MyTrait
        |""".stripMargin)
    addFileToProjectSources("org/example/package.scala",
      """package org
        |
        |package object example extends MyTrait {
        |  def itoa(n: Int): String = n.toString
        |}
        |""".stripMargin)

    val messages = compiler.make().asScala.toSeq
    assertNoErrorsOrWarnings(messages)
  }
}
