package org.jetbrains.jps.incremental.scala

import java.util.UUID

/**
 * Marker trait for resource builder enablers which we register ourselves in JPS and need to make sure that
 * we manually deregister to avoid a memory leak in the Scala Compile Server when running compiler-based
 * highlighting.
 */
trait ScalaResourceBuilderEnabler {
  val customBuildId: Option[UUID]
}
