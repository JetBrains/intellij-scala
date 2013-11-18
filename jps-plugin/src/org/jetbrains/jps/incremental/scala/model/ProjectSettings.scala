package org.jetbrains.jps.incremental.scala
package model

/**
 * Nikolay.Tropin
 * 11/18/13
 */
trait ProjectSettings {

  def incrementalType: IncrementalType

  def compileOrder: Order
}