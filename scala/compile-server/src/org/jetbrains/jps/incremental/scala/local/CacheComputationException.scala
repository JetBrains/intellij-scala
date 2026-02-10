package org.jetbrains.jps.incremental.scala.local

class CacheComputationException(cause: Throwable) extends Exception("Another thread failed to compute a cached value", cause)
