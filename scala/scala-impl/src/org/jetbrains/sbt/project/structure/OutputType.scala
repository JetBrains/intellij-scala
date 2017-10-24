package org.jetbrains.sbt
package project.structure

/**
 * @author Pavel Fatin
 */
sealed abstract class OutputType

object OutputType {
  object StdOut extends OutputType
  object StdErr extends OutputType
}
