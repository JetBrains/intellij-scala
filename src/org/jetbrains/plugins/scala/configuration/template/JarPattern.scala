package org.jetbrains.plugins.scala
package configuration.template

import java.util.regex.Pattern

import com.intellij.openapi.vfs.VirtualFile

/**
 * @author Pavel Fatin
 */
class JarPattern(prefix: String) {
  private val binary = Pattern.compile(prefix + ".*\\.jar")

  private val docs = Pattern.compile(prefix + ".*-javadoc\\.jar")

  def title: String = prefix + "*.jar"

  def isBinary(file: VirtualFile): Boolean = !isDocs(file) && binary.matcher(file.getName).matches

  def isDocs(file: VirtualFile): Boolean = docs.matcher(file.getName).matches
}

object JarPattern {
  val Library = new JarPattern("scala-library")

  val Compiler = new JarPattern("scala-compiler")

  val Reflect = new JarPattern("scala-reflect")

  val XML = new JarPattern("scala-xml")

  val Swing = new JarPattern("scala-swing")

  val Combinators = new JarPattern("scala-parser-combinators")

  val Actors = new JarPattern("scala-actors")
}
