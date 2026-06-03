package org.jetbrains.sbt.project

import org.jetbrains.sbt.project.structure.JvmOpts
import org.junit.Assert.assertEquals
import org.junit.Test

import java.nio.file.Files
import scala.util.Using

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
  def load(): Unit = {
    import org.jetbrains.sbt.PathTestUtil.tempPathReleasable
    Using.resource(Files.createTempDirectory("jvmOptsTest")) { optsDir =>
      val optsFile = optsDir.resolve(".jvmopts")
      Files.writeString(optsFile, input)
      val opts = JvmOpts.loadFrom(optsDir)
      assertEquals(expected, opts)
    }
  }
}
