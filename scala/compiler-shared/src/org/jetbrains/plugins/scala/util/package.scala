package org.jetbrains.plugins.scala

package object util {

  /**
   * Compilation identifier
   */
  type CompilationId = Long

  /**
   * Option that containing message if no value
   */
  type Opt[+A] = Either[String, A]
}
