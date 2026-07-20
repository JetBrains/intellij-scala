package org.jetbrains.jps.incremental.scala.tracing

// "Rebuild" when the compile scope forces a rebuild of the chunk's targets (e.g. the Rebuild
// Project button), "Incremental" otherwise (incremental build / automake). Surfaced on the
// external-build tracing span, which otherwise has no metadata about why the build ran.
enum BuildReason {
  case Compile, Rebuild 

  override def toString: String = this match {
    case Compile => "Incremental"
    case Rebuild => "Rebuild"
  }
}