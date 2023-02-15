package org.jetbrains.sbt.project

import com.intellij.openapi.util.io.FileUtil
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.sbt.project.structure.{SbtOption, SbtOpts}
import org.jetbrains.sbt.project.structure.SbtOption._
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



  @Test
  def testProcessArgs(): Unit = {
     def doTest(providedArgs: Seq[String], expected: Seq[SbtOption]): Unit = {
      val actual = SbtOpts.processArgs(providedArgs)
      assertThat(actual, equalTo(expected))
    }
    doTest(Seq("-d", "-color=always"), Seq(SbtLauncherOption("--debug"), JvmOptionShellOnly("-Dsbt.color=always")))
    doTest(Seq("-debug", "-color always"), Seq(SbtLauncherOption("--debug")))
    doTest(Seq("-debug", "-color=always"), Seq(SbtLauncherOption("--debug"), JvmOptionShellOnly("-Dsbt.color=always")))
    doTest(Seq("-debug", "-debug-inc", "-color=always"), Seq(SbtLauncherOption("--debug"), JvmOptionGlobal("-Dxsbt.inc.debug=true"), JvmOptionShellOnly("-Dsbt.color=always")))
    doTest(Seq("---debug", "-color"), Seq.empty)
    doTest(Seq("debug", "-debug=true"), Seq.empty)
    doTest(Seq("-debug-inc", "-color"), Seq(JvmOptionGlobal("-Dxsbt.inc.debug=true")))
  }

  @Test
  def testCombineSbtOptions(): Unit = {
    def doTest(providedArgs: Seq[String], expected: Seq[String]): Unit = {
      val actual = SbtOpts.combineSbtOptsWithArgs(providedArgs)
      assertThat(actual, equalTo(expected))
    }
    doTest(Seq("-sbt-dir", "temp dir", "-color=always", "-d", "dummy"), Seq("-sbt-dir temp dir", "-color=always", "-d", "dummy"))
    doTest(Seq("--sbt-dir", "temp dir", "-color=always", "--d", "dummy"), Seq("-sbt-dir temp dir", "-color=always", "-d", "dummy"))
    doTest(Seq("-d", "-sbt-dir"), Seq("-d", "-sbt-dir"))
    doTest(Seq("-d", "-sbt-dir", ""), Seq("-d", "-sbt-dir", ""))
    doTest(Seq("-d", "-sbt-dir", "-dummy"), Seq("-d", "-sbt-dir", "-dummy"))
  }

  @Test
  def dummyFailingTest(): Unit = {
    assertThat(3, equalTo(2))
  }
}
