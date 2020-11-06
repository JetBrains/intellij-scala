package org.jetbrains.plugins.scala.compiler.data

import java.io.File

import org.jetbrains.jps.incremental.scala.{containsDotty, containsScala3, containsScala3OrDotty}

/**
 * @author Pavel Fatin
 */
case class CompilerJars(libraries: Seq[File],
                        compiler: File,
                        extra: Seq[File]) {

  def hasDotty: Boolean =
    containsDotty(extra)
  def hasScala3: Boolean =
    containsScala3(extra)
  def hasScala3OrDotty: Boolean =
    containsScala3OrDotty(extra)

  def allJars: Seq[File] =
    libraries ++ extra :+ compiler
}
