package org.jetbrains.plugins.scala.compiler.data

import org.jetbrains.jps.incremental.scala.containsScala3

import java.io.File

/**
 * @author Pavel Fatin
 */
case class CompilerJars(libraries: Seq[File],
                        compiler: File,
                        extra: Seq[File]) {

  def hasScala3: Boolean =
    containsScala3(extra)

  def allJars: Seq[File] =
    libraries ++ extra :+ compiler
}
