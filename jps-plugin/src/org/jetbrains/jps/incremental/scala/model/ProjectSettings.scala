package org.jetbrains.jps.incremental.scala
package model

import org.jetbrains.plugin.scala.compiler.{CompileOrder, IncrementalType}

/**
 * Nikolay.Tropin
 * 11/18/13
 */
trait ProjectSettings {

  def incrementalType: IncrementalType

  def compileOrder: CompileOrder
}