package org.jetbrains.sbt

import com.intellij.execution.configurations.ParametersList
import org.jetbrains.plugins.scala.extensions.RichFile
import org.junit.Assert._
import org.junit.Test

import java.io.File

class SbtUtilTest {

  val v0120: SbtVersion = SbtVersion("0.12.0")
  val v0130: SbtVersion = SbtVersion("0.13.0")
  val v01317: SbtVersion = SbtVersion("0.13.17")
  val v100: SbtVersion = SbtVersion("1.0.0")
  val v112: SbtVersion = SbtVersion("1.1.2")
  val v200: SbtVersion = SbtVersion("2.0.0")
  val v223: SbtVersion = SbtVersion("2.2.3")

  import SbtUtil.defaultGlobalBase
  val globalBase012: File = defaultGlobalBase / "0.12"
  val globalBase013: File = defaultGlobalBase / "0.13"
  val globalBase10: File = defaultGlobalBase / "1.0"
  val globalBase20: File = defaultGlobalBase / "2.0"

  @Test
  def dummyTest(): Unit = {
    assertEquals(true, false)
  }

  @Test
  def testDefaultGlobalBase(): Unit = {
    import SbtUtil.globalBase
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
    import SbtUtil.globalPluginsDirectory
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

    import SbtUtil.globalPluginsDirectory
    val dir = globalPluginsDirectory(v0120, params)
    assertEquals(new File("hockensnock/plugins"), dir)
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
    assertEquals(new File("snickenfland"), dir)
  }

  @Test
  def testCustomGlobalPluginsFromGlobalPluginsParam3(): Unit = {
    val params = new ParametersList()
    params.addProperty("sbt.global.base", "hockensnock")
    params.add("-Dsbt.global.plugins=tocklewick")

    val dir = SbtUtil.globalPluginsDirectory(v0120, params)
    assertEquals(new File("tocklewick"), dir)
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
    assertEquals(SbtVersion("2.0.0-M3"), upgradeSbtVersionToTheLatestCompatible(SbtVersion("2.0.0-M3")))
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
