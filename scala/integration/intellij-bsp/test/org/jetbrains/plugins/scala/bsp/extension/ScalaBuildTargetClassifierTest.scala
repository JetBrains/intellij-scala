package org.jetbrains.plugins.scala.bsp.extension

import junit.framework.TestCase
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.plugins.bsp.magicmetamodel.impl.workspacemodel.{BuildTargetInfo, ModuleCapabilities}

import scala.jdk.CollectionConverters._

class ScalaBuildTargetClassifierTest extends TestCase {
  private val classifier: ScalaBuildTargetClassifier = new ScalaBuildTargetClassifier

  private val fileBuildTargetId = "file:/home/akowalski/myproject/ZPP/hello_world/#test/Test"
  private val fileBuildTargetPath = List.apply("home", "akowalski", "myproject", "ZPP", "hello_world")
  private val fileBuildTargetName = "#test/Test"

  private val fileBuildTargetInfo = new BuildTargetInfo(
    "file:/home/akowalski/myproject/ZPP/hello_world/#test/Test",
    fileBuildTargetName,
    List.empty[String].asJava,
    new ModuleCapabilities(
      false, false, false, false
    ),
    List.empty[String].asJava,
    "file:/home/akowalski/myproject/ZPP/hello_world"
  )

  def testGetBuildTargetName(): Unit = {
    val resultBuildTargetName = classifier.calculateBuildTargetName(fileBuildTargetInfo)
    assertThat(resultBuildTargetName).isEqualTo(fileBuildTargetName)
  }

  def testGetBuildTargetPath(): Unit = {
    val resultBuildTargetPath = classifier.calculateBuildTargetPath(fileBuildTargetInfo)
    assertThat(resultBuildTargetPath).isEqualTo(fileBuildTargetPath.asJava)
  }
}
