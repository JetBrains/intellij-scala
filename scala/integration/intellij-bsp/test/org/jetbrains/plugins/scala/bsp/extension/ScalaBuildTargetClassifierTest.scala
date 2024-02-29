package org.jetbrains.plugins.scala.bsp.extension

import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat

import scala.jdk.CollectionConverters._

class ScalaBuildTargetClassifierTest extends TestCase {
  private val classifier: ScalaBuildTargetClassifier = new ScalaBuildTargetClassifier

  private val fileBuildTargetId = "file:/home/akowalski/myproject/ZPP/hello_world/#test/Test"
  private val fileBuildTargetPath = List.apply("home", "akowalski", "myproject", "ZPP", "hello_world")
  private val fileBuildTargetName = "#test/Test"

  def testGetBuildTargetName(): Unit = {
    val resultBuildTargetName = classifier.calculateBuildTargetName(fileBuildTargetId)
    assertThat(resultBuildTargetName).isEqualTo(fileBuildTargetName)
  }

  def testGetBuildTargetPath(): Unit = {
    val resultBuildTargetPath = classifier.calculateBuildTargetPath(fileBuildTargetId)
    assertThat(resultBuildTargetPath).isEqualTo(fileBuildTargetPath.asJava)
  }
}
