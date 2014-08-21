package org.jetbrains.plugins.scala
package configuration.template

import java.util.regex.Pattern

import com.intellij.openapi.vfs.VirtualFile

/**
 * @author Pavel Fatin
 */
class JarDescriptor(prefix: String) {
  private val binary = Pattern.compile(prefix + ".*\\.jar")

  private val docs = Pattern.compile(prefix + ".*-javadoc\\.jar")

  def title: String = prefix + "*.jar"

  def isBinary(file: VirtualFile): Boolean = !isDocs(file) && binary.matcher(file.getName).matches

  def isDocs(file: VirtualFile): Boolean = docs.matcher(file.getName).matches
}

object JarDescriptor {
  val Library = new JarDescriptor("scala-library")

  val Compiler = new JarDescriptor("scala-compiler")

  val Reflection = new JarDescriptor("scala-reflect")

  val XML = new JarDescriptor("scala-xml")

  val Swing = new JarDescriptor("scala-swing")

  val Combinators = new JarDescriptor("scala-parser-combinators")

  val Actors = new JarDescriptor("scala-actors")
}
