package org.jetbrains.jps.incremental.scala
package model

/**
 * Nikolay.Tropin
 * 11/18/13
 */
class ProjectSetingsImpl extends ProjectSettings{

  def incrementalType: IncrementalType = IncrementalType.IDEA

  def compileOrder: Order = Order.Mixed
}
