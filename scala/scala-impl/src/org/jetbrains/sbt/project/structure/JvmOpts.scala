package org.jetbrains.sbt
package project.structure

import java.io.File
import com.intellij.openapi.util.io.FileUtil
import scala.collection.JavaConverters._

/**
  * Support for the .jvmopts file loaded by the sbt launcher script as alternative to command line options.
  */
object JvmOpts {

  def loadFrom(directory: File): Seq[String] = {
    val jvmOptsFile = directory / ".jvmopts"
    if (jvmOptsFile.exists && jvmOptsFile.isFile && jvmOptsFile.canRead)
      FileUtil.loadLines(jvmOptsFile).asScala.map(_.trim).filter(_.startsWith("-"))
    else
      Seq.empty
  }

}
