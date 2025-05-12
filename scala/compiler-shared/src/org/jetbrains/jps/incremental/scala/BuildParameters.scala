package org.jetbrains.jps.incremental.scala

/**
 * Custom JPS build parameters set for all JPS builds running inside the Scala Compile Server
 * when compiler-based highlighting is enabled.
 */
object BuildParameters {
  final val BuildTriggeredByCBH = "scala_build_triggered_by_cbh"

  final val CustomBuildIdForCBH = "scala_custom_build_id_for_cbh"
}
