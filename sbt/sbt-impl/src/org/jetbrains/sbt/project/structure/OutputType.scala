package org.jetbrains.sbt
package project.structure

import com.intellij.openapi.util.Key

sealed abstract class OutputType(val name: String)

object OutputType {
  object StdOut extends OutputType("stdout")
  object StdErr extends OutputType("stderr")
  object MySystem extends OutputType("system")
  final case class Other(key: Key[?]) extends OutputType(key.toString)
}
