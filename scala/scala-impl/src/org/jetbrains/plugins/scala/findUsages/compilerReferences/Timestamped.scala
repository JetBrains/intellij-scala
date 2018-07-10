package org.jetbrains.plugins.scala.findUsages.compilerReferences

final case class Timestamped[T](timestamp: Long, unwrap: T)
