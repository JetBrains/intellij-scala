package org.jetbrains.plugins.scala.compiler.highlighting

import scala.collection.immutable.SortedSet

private final case class ScheduledCompilationRequests(scheduledTimestamp: Long, requests: SortedSet[CompilationRequest])
