package org.jetbrains.bsp.project.importing

import ch.epfl.scala.bsp4j.{BuildTarget, BuildTargetCapabilities, BuildTargetIdentifier, SourceItem, SourceItemKind, SourcesItem}
import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.LocalEelDescriptor
import org.jetbrains.bsp.project.importing.BspResolverDescriptors.ModuleKind
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test

import scala.jdk.CollectionConverters.*

class BspResolverLogicTest {

  private given EelDescriptor = LocalEelDescriptor.INSTANCE

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

  /**
   * The project has 10 modules. Each module has a `jvm` and a `js` target:
   *  - both targets use the same `shared` source directory
   *  - each target has its own generated source directory (`src_managed`)
   *
   * Every module should get one shared sources module, which also contains the generated sources of its two targets.
   * This should be fast.
   */
  @Test(timeout = 45000)
  def testCalculateModuleDescriptionsWithManySharedSourceGroupsAndGeneratedSources(): Unit = {
    val root = "file:/project/"
    def directory(path: String, generated: Boolean) =
      new SourceItem(s"$root$path/", SourceItemKind.DIRECTORY, generated)

    val targetsWithSources = for {
      module <- (1 to 10).map(i => s"module$i")
      platform <- Seq("jvm", "js")
    } yield {
      val target = dummyTarget(s"$root#$module-$platform", s"$module-$platform")
      target.setBaseDirectory(s"$root$module/$platform/")
      val sources = Seq(
        directory(s"$module/shared", generated = false),
        directory(s"$module/$platform/src_managed", generated = true),
      )
      (target, new SourcesItem(target.getId, sources.asJava))
    }
    val (targets, sourcesItems) = targetsWithSources.unzip

    val start = System.nanoTime()
    val descriptions = BspResolverLogic.calculateModuleDescriptions(targets, Nil, Nil, sourcesItems, Nil, Nil, Nil)
    val elapsed = java.time.Duration.ofNanos(System.nanoTime() - start)

    val sharedModules = descriptions.synthetic
    assertEquals(10, sharedModules.size)
    sharedModules.foreach { sharedModule =>
      assertEquals(2, sharedModule.data.sourceRoots.count(_.generated))
    }
    val allGeneratedRoots = sharedModules.flatMap(_.data.sourceRoots.filter(_.generated))
    assertEquals("every generated source directory should be in only one shared sources module", 20, allGeneratedRoots.distinct.size)
    assertTrue(s"calculateModuleDescriptions took $elapsed", elapsed.toSeconds < 10)
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
