package org.jetbrains.sbt.project

import java.io.File

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.sbt.project.structure.{JvmOpts, SbtOpts}
import org.junit.Test
import org.junit.Assert._

class JvmOptsTest {

  private val input =
    """
      |# My jvm options
      |-Xmx2G
      |-Dhoodlump=bloom
    """.stripMargin

  private val expected = Seq(
    "-Xmx2G",
    "-Dhoodlump=bloom"
  )

  @Test
  def testLoad(): Unit = {
    val optsDir = FileUtil.createTempDirectory("jvmOptsTest","",true)
    val optsFile = new File(optsDir,".jvmopts")
    FileUtil.writeToFile(optsFile, input)
    val opts = JvmOpts.loadFrom(optsDir)
    assertEquals(expected, opts)
  }
}
