package org.jetbrains.plugins.scala.compiler.data

import java.io.File

import org.jetbrains.jps.incremental.scala.containsDotty

/**
 * @author Pavel Fatin
 */
case class CompilerJars(libraries: Seq[File],
                        compiler: File,
                        extra: collection.Seq[File]) {

  def hasDotty: Boolean =
    containsDotty(extra)

  def allJars: collection.Seq[File] =
    libraries ++ extra :+ compiler
}
