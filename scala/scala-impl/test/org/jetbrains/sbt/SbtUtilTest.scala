package org.jetbrains.sbt

import java.io.File

import com.intellij.execution.configurations.ParametersList
import org.jetbrains.plugins.scala.project.Version
import org.jetbrains.sbt.SbtUtil._
import org.junit.Assert._
import org.junit.Test

class SbtUtilTest {

  val v0120 = Version("0.12.0")
  val v0130 = Version("0.13.0")
  val v01317 = Version("0.13.17")
  val v100 = Version("1.0.0")
  val v112 = Version("1.1.2")
  val v200 = Version("2.0.0")
  val v223 = Version("2.2.3")

  val globalBase012: File = defaultGlobalBase / "0.12"
  val globalBase013: File = defaultGlobalBase / "0.13"
  val globalBase10: File = defaultGlobalBase / "1.0"
  val globalBase20: File = defaultGlobalBase / "2.0"

  @Test
  def testDefaultGlobalBase(): Unit = {
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

    val dir = globalPluginsDirectory(v0120, params)
    assertEquals(new File("hockensnock"), dir)
  }

  @Test
  def testCustomGlobalPluginsWithEmptyPluginsParam(): Unit = {
    val params = new ParametersList()

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

    val dir = globalPluginsDirectory(v0120, params)
    assertEquals(new File("snickenfland"), dir)
  }

  @Test
  def testCustomGlobalPluginsFromGlobalPluginsParam3(): Unit = {
    val params = new ParametersList()
    params.addProperty("sbt.global.base", "hockensnock")
    params.add("-Dsbt.global.plugins=tocklewick")
    val dir = globalPluginsDirectory(v0120, params)
    assertEquals(new File("tocklewick"), dir)
  }

}
