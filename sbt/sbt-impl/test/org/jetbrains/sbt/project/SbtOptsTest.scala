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

  @Test
  def testLoad(): Unit = {
    val input =
      """
        |--sbt-boot /some/where/sbt/boot -sbt-dir      /some/where/else/sbt
        |--ivy /some/where/ivy -color=   always -debug
        |-jvm-debug 4711
        |--d
        |-error
        |debug
        |--warn
        |--debug
        |
      """.stripMargin
    val expected = Seq(
      JvmOptionGlobal("-Dsbt.boot.directory=/some/where/sbt/boot")(),
      JvmOptionGlobal("-Dsbt.global.base=/some/where/else/sbt")(),
      JvmOptionGlobal("-Dsbt.ivy.home=/some/where/ivy")(),
      SbtLauncherOption("--debug")(),
      JvmOptionGlobal("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=4711")(),
      SbtLauncherOption("--error")(),
      SbtLauncherOption("--warn")(),
      SbtLauncherOption("--debug")()
    )

    val optsDir = FileUtil.createTempDirectory("sbtOptsTest","",true)
    val optsFile = new File(optsDir,SbtOpts.SbtOptsFile)
    FileUtil.writeToFile(optsFile, input)
    val opts = SbtOpts.loadFrom(optsDir)(null)
    assertEquals(expected, opts)
  }
  @Test
  def testWithComments(): Unit = {
    val input =
      """
        |#--sbt-boot /some/where/sbt/boot -sbt-dir      /some/where/else/sbt
        |--ivy /some/where/ivy -color=   always -debug #-jvm-debug 4711
        |--d "#" -error
        |debug #--sbt-boot /some/where/sbt/boot
        |--warn
        |--debug
        |
      """.stripMargin
    val expected = Seq(
      JvmOptionGlobal("-Dsbt.ivy.home=/some/where/ivy")(),
      SbtLauncherOption("--debug")(),
      SbtLauncherOption("--error")(),
      SbtLauncherOption("--warn")(),
      SbtLauncherOption("--debug")()
    )

    val optsDir = FileUtil.createTempDirectory("sbtOptsTest", "", true)
    val optsFile = new File(optsDir, SbtOpts.SbtOptsFile)
    FileUtil.writeToFile(optsFile, input)
    val opts = SbtOpts.loadFrom(optsDir)(null)
    assertEquals(expected, opts)
  }

  @Test
  def testMapOptionsToSbtOptions(): Unit = {
     def doTest(providedArgs: Seq[String], expected: Seq[SbtOption]): Unit = {
      val actual = SbtOpts.mapOptionsToSbtOptions(providedArgs, "dummy/Path")(null)
      assertThat(actual, equalTo(expected))
    }
    doTest(Seq("-d", "-color=always", "-error"), Seq(SbtLauncherOption("--debug")(), JvmOptionShellOnly("-Dsbt.color=always")(), SbtLauncherOption("--error")()))
    doTest(Seq("-d", "-color=   always", "--d"), Seq(SbtLauncherOption("--debug")()))
    doTest(Seq("-debug", "-color always", "-error"), Seq(SbtLauncherOption("--debug")(), SbtLauncherOption("--error")()))
    doTest(Seq("-d", "-no-global"), Seq(SbtLauncherOption("--debug")(), JvmOptionGlobal("-Dsbt.global.base=dummy/Path/project/.sbtboot")()))
    doTest(Seq("-debug", "-color=always"), Seq(SbtLauncherOption("--debug")(), JvmOptionShellOnly("-Dsbt.color=always")()))
    doTest(Seq("-debug", "-debug-inc", "-color=always"), Seq(SbtLauncherOption("--debug")(), JvmOptionGlobal("-Dxsbt.inc.debug=true")(), JvmOptionShellOnly("-Dsbt.color=always")()))
    doTest(Seq("---debug", "-color"), Seq.empty)
    doTest(Seq("debug", "-debug=true"), Seq.empty)
    doTest(Seq("-debug-inc", "-color"), Seq(JvmOptionGlobal("-Dxsbt.inc.debug=true")()))
    doTest(Seq("-sbt-dirop"), Seq.empty)
    doTest(Seq("""-sbt-dir "/Users/a  """", """-sbt-dir "/User's/a  """"), Seq(JvmOptionGlobal("-Dsbt.global.base=\"/Users/a  \"")(), JvmOptionGlobal("-Dsbt.global.base=\"/User's/a  \"")()))
  }

  @Test
  def testCombineOptionsWithArgs(): Unit = {
    def doTest(providedOpts: String, expected: Seq[String]): Unit = {
      val actual = SbtOpts.combineOptionsWithArgs(providedOpts)
      assertThat(actual, equalTo(expected))
    }
    doTest(""" -sbt-dir "temp dir" -color=always -d dummy """, Seq("-sbt-dir temp dir", "-color=always", "-d", "dummy"))
    doTest(""" --sbt-dir "temp di'r" -color=always  --d dummy """, Seq("-sbt-dir temp di'r", "-color=always", "--d", "dummy"))
    doTest(""" --sbt-dir "temp dir -color=always  --d dummy """, Seq.empty)
    doTest("-d -sbt-dir", Seq("-d", "-sbt-dir"))
    doTest(""" -d -sbt-dir "" """, Seq("-d", "-sbt-dir", ""))
    doTest("-d -sbt-dir -dummy", Seq("-d", "-sbt-dir", "-dummy"))
  }

  import org.junit.Assert.assertTrue
  @Test
  def dummyFailingTest(): Unit = {
    assertTrue(false)
  }

}
