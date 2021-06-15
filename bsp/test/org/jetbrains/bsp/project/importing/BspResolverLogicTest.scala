package org.jetbrains.bsp.project.importing

import ch.epfl.scala.bsp4j.{BuildTarget, BuildTargetCapabilities, BuildTargetIdentifier, BuildTargetTag, SourceItem, SourceItemKind, SourcesItem}
import org.jetbrains.bsp.project.importing.BspResolverDescriptors.UnspecifiedModule
import org.junit.Test

import java.nio.file.Paths
import scala.jdk.CollectionConverters._

class BspResolverLogicTest {

  /** When base dir is empty, only root module is created */
  @Test
  def testCalculateModuleDescriptionsEmptyBaseDir(): Unit = {

    val target = new BuildTarget(
      new BuildTargetIdentifier("ePzqj://jqke:540/n/ius7/jDa/t/z78"),
      List("bla").asJava, null, List.empty.asJava,
      new BuildTargetCapabilities(true,true,true)
    )

    val descriptions = BspResolverLogic.calculateModuleDescriptions(List(target), Nil, Nil, Nil, Nil, Nil)

    assert(descriptions.synthetic.isEmpty)
    assert(descriptions.modules.size == 1)
    val rootModule = descriptions.modules.head
    assert(rootModule.moduleKindData == UnspecifiedModule())
    assert(rootModule.data.targets.head == target)
  }

  @Test
  def testCalculateModuleDescriptionsBaseDirWithTest(): Unit = {
    val target = new BuildTarget(
      new BuildTargetIdentifier("ePzqj://jqke:540/n/ius7/jDa/t/z78"),
      List(BuildTargetTag.TEST).asJava, null, List.empty.asJava,
      new BuildTargetCapabilities(true, true, true)
    )

    val path = Paths.get(".").toUri.toString
    val items = new SourcesItem(target.getId, List(new SourceItem(path, SourceItemKind.DIRECTORY, false)).asJava)

    val descriptions = BspResolverLogic.calculateModuleDescriptions(List(target), Nil, Nil, Seq(items), Nil, Nil)
    assert(descriptions.modules.size == 1)
    assert(descriptions.modules.flatMap(_.data.testSourceDirs).size == 1)
    assert(descriptions.modules.flatMap(_.data.sourceDirs).isEmpty)
  }

  @Test
  def testCalculateModuleDescriptionsBaseDirWithIntegrationTest(): Unit = {
    val target = new BuildTarget(
      new BuildTargetIdentifier("ePzqj://jqke:540/n/ius7/jDa/t/z78"),
      List(BuildTargetTag.INTEGRATION_TEST).asJava, null, List.empty.asJava,
      new BuildTargetCapabilities(true, true, true)
    )

    val path = Paths.get(".").toUri.toString
    val items = new SourcesItem(target.getId, List(new SourceItem(path, SourceItemKind.DIRECTORY, false)).asJava)

    val descriptions = BspResolverLogic.calculateModuleDescriptions(List(target), Nil, Nil, Seq(items), Nil, Nil)
    assert(descriptions.modules.size == 1)
    assert(descriptions.modules.flatMap(_.data.testSourceDirs).size == 1)
    assert(descriptions.modules.flatMap(_.data.sourceDirs).isEmpty)
  }

}
