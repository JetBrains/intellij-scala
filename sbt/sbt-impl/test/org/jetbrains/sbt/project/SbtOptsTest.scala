package org.jetbrains.sbt.project

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.sbt.project.structure.SbtOpts
import org.junit.Assert.assertEquals
import org.junit.Test

import java.io.File

class SbtOptsTest {

  private val input =
    """
      |-sbt-boot /some/where/sbt/boot
      |-sbt-dir /some/where/else/sbt
      |-ivy /some/where/ivy
      |-jvm-debug 4711
      |
    """.stripMargin

  private val expected = Seq(
    "-Dsbt.boot.directory=/some/where/sbt/boot",
    "-Dsbt.global.base=/some/where/else/sbt",
    "-Dsbt.ivy.home=/some/where/ivy",
    "-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=4711"
  )

  @Test
  def dummyFailingTest(): Unit = {
    assertEquals(3, 2)
  }

  @Test
  def testLoad(): Unit = {
    val optsDir = FileUtil.createTempDirectory("sbtOptsTest","",true)
    val optsFile = new File(optsDir,SbtOpts.SbtOptsFile)
    FileUtil.writeToFile(optsFile, input)
    val opts = SbtOpts.loadFrom(optsDir)
    assertEquals(expected, opts)
  }
}
