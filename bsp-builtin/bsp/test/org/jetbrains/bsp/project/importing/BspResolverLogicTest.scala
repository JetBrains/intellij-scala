package org.jetbrains.bsp.project.importing

import ch.epfl.scala.bsp4j.{BuildTarget, BuildTargetCapabilities, BuildTargetIdentifier}
import org.jetbrains.bsp.project.importing.BspResolverDescriptors.ModuleKind
import org.junit.Assert.assertEquals
import org.junit.Test

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

    val descriptions = BspResolverLogic.calculateModuleDescriptions(List(target), Nil, Nil, Nil, Nil, Nil, Nil)

    assert(descriptions.synthetic.isEmpty)
    assert(descriptions.modules.size == 1)
    val rootModule = descriptions.modules.head
    assert(rootModule.moduleKindData == ModuleKind.UnspecifiedModule())
    assert(rootModule.data.targets.head == target)
  }

  private def dummyTarget(id: String, displayName: String) = {
    def emptyList[T]: java.util.List[T] = List().asJava

    val target = new BuildTarget(
      new BuildTargetIdentifier(id),
      emptyList, null, emptyList,
      new BuildTargetCapabilities(true, true, true)
    )
    target.setDisplayName(displayName)
    target
  }

  @Test
  def testSharedModuleTargetIdAndName(): Unit = {
    val targets = Seq(
      dummyTarget("file:///C:/Users/user/projects/mill-intellij/dummy/amm/2.12.17?id=dummy.amm[2.12.17]", "dummy.amm[2.12.17]"),
      dummyTarget("file:///C:/Users/user/projects/mill-intellij/dummy/amm/2.13.10?id=dummy.amm[2.13.10]", "dummy.amm[2.13.10]")
    )
    assertEquals(
      BspResolverLogic.TargetIdAndName(
        "file:/dummyPathForSharedSourcesModule?id=dummy.amm[(2.12.17+2.13.10)]",
        "dummy.amm[(2.12.17+2.13.10)] (shared)"
      ),
      BspResolverLogic.sharedModuleTargetIdAndName(targets)
    )
  }
}
