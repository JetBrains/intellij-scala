package org.jetbrains.plugins.scala.compiler.data

import java.io.File

import org.jetbrains.jps.incremental.scala.containsDotty

/**
 * @author Pavel Fatin
 */
case class CompilerJars(library: File,
                        compiler: File,
                        extra: Seq[File]) {

  def hasDotty: Boolean =
    containsDotty(extra)

  def allJars: Seq[File] =
    library +: compiler +: extra
}
