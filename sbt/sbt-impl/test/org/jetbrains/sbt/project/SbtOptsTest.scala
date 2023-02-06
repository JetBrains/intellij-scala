package org.jetbrains.sbt.project

import com.intellij.openapi.util.io.FileUtil
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.sbt.project.structure.SbtOpts
import org.jetbrains.sbt.project.structure.SbtOpts.{JvmOptionGlobal, JvmOptionShellOnly, SbtLauncherOption, CommandOption}
import org.junit.Assert.assertEquals
import org.junit.Test

import java.io.File

class SbtOptsTest {

  private val input =
    """
      |--sbt-boot /some/where/sbt/boot
      |-sbt-dir /some/where/else/sbt
      |--ivy /some/where/ivy
      |-jvm-debug 4711
      |-debug
      |debug
      |
    """.stripMargin

  private val expected = Seq(
    JvmOptionGlobal("-Dsbt.boot.directory=/some/where/sbt/boot"),
    JvmOptionGlobal("-Dsbt.global.base=/some/where/else/sbt"),
    JvmOptionGlobal("-Dsbt.ivy.home=/some/where/ivy"),
    JvmOptionGlobal("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=4711"),
    SbtLauncherOption("--debug")
  )

  @Test
  def testLoad(): Unit = {
    val optsDir = FileUtil.createTempDirectory("sbtOptsTest","",true)
    val optsFile = new File(optsDir,SbtOpts.SbtOptsFile)
    FileUtil.writeToFile(optsFile, input)
    val opts = SbtOpts.loadFrom(optsDir)
    assertEquals(expected, opts)
  }

  private def doTest(providedArgs: Seq[String], expected: Seq[CommandOption]): Unit = {
    val actual = SbtOpts.processArgs(providedArgs)
    assertThat(actual, equalTo(expected))
  }

  @Test
  def testProcessArgs(): Unit = {
    doTest(Seq("-d", "-color=always"), Seq(SbtLauncherOption("--debug"), JvmOptionShellOnly("-Dsbt.color=always")))
    doTest(Seq("-debug", "-color always"), Seq(SbtLauncherOption("--debug"), JvmOptionShellOnly("-Dsbt.color=always")))
    doTest(Seq("--debug", "-color always", "-color=always"), Seq(SbtLauncherOption("--debug"), JvmOptionShellOnly("-Dsbt.color=always"), JvmOptionShellOnly("-Dsbt.color=always")))
    doTest(Seq("--debug", "-debug-inc", "--color=always"), Seq(SbtLauncherOption("--debug"), JvmOptionGlobal("-Dxsbt.inc.debug=true"), JvmOptionShellOnly("-Dsbt.color=always")))
    doTest(Seq("---debug", "-color"), Seq.empty)
    doTest(Seq("debug", "-debug=true"), Seq.empty)
    doTest(Seq("-debug-inc", "-color"), Seq(JvmOptionGlobal("-Dxsbt.inc.debug=true")))
  }
}
