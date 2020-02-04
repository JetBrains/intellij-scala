package org.jetbrains.jps
package incremental.scala
package data

import java.io.File

/**
 * @author Pavel Fatin
 */
case class CompilerJars(library: File,
                        compiler: File,
                        extra: Seq[File]) {

  def hasDotty: Boolean =
    CompilerJars.hasDotty(extra)
}

object CompilerJars {

  def hasDotty(files: Seq[File]): Boolean =
    files.exists(_.getName.startsWith("dotty"))
}
