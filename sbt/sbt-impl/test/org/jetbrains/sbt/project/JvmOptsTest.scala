package org.jetbrains.sbt.project

import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.sbt.project.structure.JvmOpts
import org.junit.Assert._
import org.junit.Test

import java.io.File

class JvmOptsTest {

  private val input =
    """
      |# My jvm options
      |-Xmx2G # -Dsbt.color=always
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
