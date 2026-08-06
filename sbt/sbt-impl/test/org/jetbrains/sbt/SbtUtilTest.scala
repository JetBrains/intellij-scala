package org.jetbrains.sbt

import com.intellij.execution.configurations.ParametersList
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.extensions.PathExt
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.nio.file.Path

@RunWith(classOf[JUnit4])
class SbtUtilTest extends UsefulTestCase {

  private val v0120: SbtVersion = SbtVersion("0.12.0")
  private val v0130: SbtVersion = SbtVersion("0.13.0")
  private val v01317: SbtVersion = SbtVersion("0.13.17")
  private val v100: SbtVersion = SbtVersion("1.0.0")
  private val v112: SbtVersion = SbtVersion("1.1.2")
  private val v200: SbtVersion = SbtVersion("2.0.0")
  private val v223: SbtVersion = SbtVersion("2.2.3")

  private val globalBase012 = SbtUtil.defaultGlobalBase(LocalEelDescriptor.INSTANCE) / "0.12"
  private val globalBase013 = SbtUtil.defaultGlobalBase(LocalEelDescriptor.INSTANCE) / "0.13"
  private val globalBase10 = SbtUtil.defaultGlobalBase(LocalEelDescriptor.INSTANCE) / "1.0"
  private val globalBase20 = SbtUtil.defaultGlobalBase(LocalEelDescriptor.INSTANCE) / "2"

  @Test
  def testDefaultGlobalBase(): Unit = {
    val globalBase = (version: SbtVersion) => SbtUtil.globalBase(version, LocalEelDescriptor.INSTANCE)
    assertEquals(globalBase012, globalBase(v0120))
    assertEquals(globalBase013, globalBase(v0130))
    assertEquals(globalBase013, globalBase(v01317))
    assertEquals(globalBase10, globalBase(v100))
    assertEquals(globalBase10, globalBase(v112))
    assertEquals(globalBase20, globalBase(v200))
    assertEquals(globalBase20, globalBase(v223))
  }

  @Test
  def testDefaultGlobalPluginsDirectory(): Unit = {
    val globalPluginsDirectory = (version: SbtVersion) => SbtUtil.globalPluginsDirectory(version, LocalEelDescriptor.INSTANCE)
    assertEquals(globalBase012 / "plugins", globalPluginsDirectory(v0120))
    assertEquals(globalBase013 / "plugins", globalPluginsDirectory(v0130))
    assertEquals(globalBase013 / "plugins", globalPluginsDirectory(v01317))
    assertEquals(globalBase10 / "plugins", globalPluginsDirectory(v100))
    assertEquals(globalBase10 / "plugins", globalPluginsDirectory(v112))
    assertEquals(globalBase20 / "plugins", globalPluginsDirectory(v200))
    assertEquals(globalBase20 / "plugins", globalPluginsDirectory(v223))
  }

  @Test
  def testCustomGlobalPluginsFromGlobalBaseParam(): Unit = {
    val params = new ParametersList()
    params.addProperty("sbt.global.base", "hockensnock")

    val dir = SbtUtil.globalPluginsDirectory(v0120, params, LocalEelDescriptor.INSTANCE)
    assertEquals(Path.of("hockensnock", "plugins"), dir)
  }

  @Test
  def testCustomGlobalPluginsWithEmptyPluginsParam(): Unit = {
    val params = new ParametersList()

    import SbtUtil.globalPluginsDirectory
    val local = LocalEelDescriptor.INSTANCE
    val expected1 = globalPluginsDirectory(v0120, local)
    val actual1 = globalPluginsDirectory(v0120, params, local)
    assertEquals(expected1, actual1)

    val expected2 = globalPluginsDirectory(v112, local)
    val actual2 = globalPluginsDirectory(v112, params, local)
    assertEquals(expected2, actual2)
  }

  @Test
  def testCustomGlobalPluginsFromGlobalPluginsParam2(): Unit = {
    val params = new ParametersList()
    params.addProperty("sbt.global.plugins", "snickenfland")

    val dir = SbtUtil.globalPluginsDirectory(v0120, params, LocalEelDescriptor.INSTANCE)
    assertEquals(Path.of("snickenfland"), dir)
  }

  @Test
  def testCustomGlobalPluginsFromGlobalPluginsParam3(): Unit = {
    val params = new ParametersList()
    params.addProperty("sbt.global.base", "hockensnock")
    params.add("-Dsbt.global.plugins=tocklewick")

    val dir = SbtUtil.globalPluginsDirectory(v0120, params, LocalEelDescriptor.INSTANCE)
    assertEquals(Path.of("tocklewick"), dir)
  }

  @Test
  def testUpgradeSbtVersionToTheLatestCompatible(): Unit = {
    import org.jetbrains.sbt.SbtVersion.upgradeSbtVersionToTheLatestCompatible

    assertEquals(SbtVersion.Latest.Sbt_0_13, upgradeSbtVersionToTheLatestCompatible(v0130))
    assertEquals(SbtVersion.Latest.Sbt_0_13, upgradeSbtVersionToTheLatestCompatible(v01317))
    assertEquals(SbtVersion.Latest.Sbt_1, upgradeSbtVersionToTheLatestCompatible(v100))
    assertEquals(SbtVersion.Latest.Sbt_1, upgradeSbtVersionToTheLatestCompatible(v112))
    assertEquals(SbtVersion.Latest.Sbt_1, upgradeSbtVersionToTheLatestCompatible(SbtVersion.Latest.Sbt_1))
    assertEquals(SbtVersion.Latest.Sbt_LatestIncludingUnreleased, upgradeSbtVersionToTheLatestCompatible(SbtVersion.Latest.Sbt_LatestIncludingUnreleased))

    assertEquals(SbtVersion("1.9001.1"), upgradeSbtVersionToTheLatestCompatible(SbtVersion("1.9001.1")))
    assertEquals(SbtVersion.Latest.Sbt_2, upgradeSbtVersionToTheLatestCompatible(SbtVersion("2.0.0-M3")))
    assertEquals(SbtVersion.Latest.Sbt_2, upgradeSbtVersionToTheLatestCompatible(SbtVersion("2.0.0")))
  }

  @Test
  def testGetSbtStructureJar(): Unit = {
    val all = SbtVersion.Latest.AllSbt1 ++ SbtVersion.Latest.AllSbt2
    val allMinor = all.flatMap(_.generateAllMinorVersions).map(SbtVersion(_))
    allMinor.foreach { version =>
      val repoDir = SbtUtil.getRepoDir(LocalEelDescriptor.INSTANCE)
      val maybeFile = SbtUtil.getSbtStructureJar(version, repoDir)
      assertTrue(s"Can't detect sbt-structure.jar for $version", maybeFile.isDefined)
    }
  }
}
