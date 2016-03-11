package org.jetbrains.jps.incremental.scala
package data

import java.io.File

/**
 * @author Pavel Fatin
 */
case class CompilerJars(library: File, compiler: File, extra: Seq[File]) {
  def files: Seq[File] = library +: compiler +: extra

  def dotty: Option[File] = extra.find(_.getName.startsWith("dotty"))
}
