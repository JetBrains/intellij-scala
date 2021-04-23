package org.jetbrains.sbt
package project.structure

import com.intellij.openapi.util.Key

/**
 * @author Pavel Fatin
 */
sealed abstract class OutputType

object OutputType {
  object StdOut extends OutputType
  object StdErr extends OutputType
  object MySystem extends OutputType
  final case class Other(key: Key[_]) extends OutputType
}
