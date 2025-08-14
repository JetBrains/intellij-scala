package org.jetbrains.sbt

import com.intellij.execution.configurations.ParametersList
import org.jetbrains.plugins.scala.extensions.PathExt
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.Test

import java.nio.file.Path

class SbtUtilTest {

  private val v0120: SbtVersion = SbtVersion("0.12.0")
  private val v0130: SbtVersion = SbtVersion("0.13.0")
  private val v01317: SbtVersion = SbtVersion("0.13.17")
  private val v100: SbtVersion = SbtVersion("1.0.0")
  private val v112: SbtVersion = SbtVersion("1.1.2")
  private val v200: SbtVersion = SbtVersion("2.0.0")
  private val v223: SbtVersion = SbtVersion("2.2.3")

  import SbtUtil.defaultGlobalBase
  private val globalBase012 = defaultGlobalBase.toPath / "0.12"
  private val globalBase013 = defaultGlobalBase.toPath / "0.13"
  private val globalBase10 = defaultGlobalBase.toPath / "1.0"
  private val globalBase20 = defaultGlobalBase.toPath / "2"

  @Test
  def testDefaultGlobalBase(): Unit = {
    import SbtUtil.globalBase
    assertEquals(globalBase012, globalBase(v0120).toPath)
    assertEquals(globalBase013, globalBase(v0130).toPath)
    assertEquals(globalBase013, globalBase(v01317).toPath)
    assertEquals(globalBase10, globalBase(v100).toPath)
    assertEquals(globalBase10, globalBase(v112).toPath)
    assertEquals(globalBase20, globalBase(v200).toPath)
    assertEquals(globalBase20, globalBase(v223).toPath)
  }

  @Test
  def testDefaultGlobalPluginsDirectory(): Unit = {
    import SbtUtil.globalPluginsDirectory
    assertEquals(globalBase012 / "plugins", globalPluginsDirectory(v0120).toPath)
    assertEquals(globalBase013 / "plugins", globalPluginsDirectory(v0130).toPath)
    assertEquals(globalBase013 / "plugins", globalPluginsDirectory(v01317).toPath)
    assertEquals(globalBase10 / "plugins", globalPluginsDirectory(v100).toPath)
    assertEquals(globalBase10 / "plugins", globalPluginsDirectory(v112).toPath)
    assertEquals(globalBase20 / "plugins", globalPluginsDirectory(v200).toPath)
    assertEquals(globalBase20 / "plugins", globalPluginsDirectory(v223).toPath)
  }

  @Test
  def testCustomGlobalPluginsFromGlobalBaseParam(): Unit = {
    val params = new ParametersList()
    params.addProperty("sbt.global.base", "hockensnock")

    import SbtUtil.globalPluginsDirectory
    val dir = globalPluginsDirectory(v0120, params)
    assertEquals(Path.of("hockensnock", "plugins"), dir.toPath)
  }

  @Test
  def testCustomGlobalPluginsWithEmptyPluginsParam(): Unit = {
    val params = new ParametersList()

    import SbtUtil.globalPluginsDirectory
    val expected1 = globalPluginsDirectory(v0120)
    val actual1 = globalPluginsDirectory(v0120, params)
    assertEquals(expected1, actual1)

    val expected2 = globalPluginsDirectory(v112)
    val actual2 = globalPluginsDirectory(v112, params)
    assertEquals(expected2, actual2)
  }

  @Test
  def testCustomGlobalPluginsFromGlobalPluginsParam2(): Unit = {
    val params = new ParametersList()
    params.addProperty("sbt.global.plugins", "snickenfland")

    val dir = SbtUtil.globalPluginsDirectory(v0120, params)
    assertEquals(Path.of("snickenfland"), dir.toPath)
  }

  @Test
  def testCustomGlobalPluginsFromGlobalPluginsParam3(): Unit = {
    val params = new ParametersList()
    params.addProperty("sbt.global.base", "hockensnock")
    params.add("-Dsbt.global.plugins=tocklewick")

    val dir = SbtUtil.globalPluginsDirectory(v0120, params)
    assertEquals(Path.of("tocklewick"), dir.toPath)
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
    assertEquals(SbtVersion("2.0.0-RC2"), upgradeSbtVersionToTheLatestCompatible(SbtVersion("2.0.0-M3")))
    assertEquals(SbtVersion("2.0.0"), upgradeSbtVersionToTheLatestCompatible(SbtVersion("2.0.0")))
  }

  @Test
  def testAreQuotesClosedCorrectly(): Unit = {
    val inputs = Seq(
      """ "sb's'b" """, """ "sb'sb" #aaaa """,
      """ 'sb"sb''#fdfdfd' """, """ 'sb"sb'"b #" c """,
      """ "sb's'#b """, """ 'sb"sb'b  " """, """ 'sb"sb'b  "c """, """ 'sb"sb'b  'c """, """ 'sb"sb"b  c """,
      """ 'sb"sb"b'#  c """, """#'sb"sb"b'#  c """
    )
    val outputs = Seq(
      Some(""" "sb's'b" """), Some(""" "sb'sb" """),
      Some(""" 'sb"sb''#fdfdfd' """), Some(""" 'sb"sb'"b #" c """),
      None, None, None, None, None,
      Some(""" 'sb"sb"b'"""), Some("")
    )
    inputs.zip(outputs).foreach { case (input, output) =>
      assertEquals(output, SbtUtil.removeCommentedOutPartsAndCheckQuotes(input))
    }
  }

  @Test
  def testGetSbtStructureJar(): Unit = {
    val all = SbtVersion.Latest.AllSbt1 ++ SbtVersion.Latest.AllSbt2
    val allMinor = all.flatMap(_.generateAllMinorVersions).map(SbtVersion(_))
    allMinor.foreach { version =>
      val maybeFile = SbtUtil.getSbtStructureJar(version)
      assertTrue(s"Can't detect sbt-structure.jar for $version", maybeFile.isDefined)
    }
  }
}
