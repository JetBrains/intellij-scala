package org.jetbrains.plugins.scala.debugger.interop

import com.intellij.debugger.NoDataException
import com.intellij.debugger.PositionManagerAsync
import com.intellij.debugger.SourcePosition
import com.sun.jdi.Location

/**
 * A [PositionManagerAsync] whose positions are computed by the blocking [getSourcePosition].
 *
 * The platform calls [getSourcePosition] of a position manager that isn't a [PositionManagerAsync] in a read action.
 * Implementing this interface makes it call [getSourcePosition] outside of read actions, so [getSourcePosition] can
 * communicate with the debuggee, and it must take the read actions it needs itself.
 */
@Suppress("UnstableApiUsage")
interface BlockingPositionManagerAsync : PositionManagerAsync {

  override suspend fun getSourcePositionAsync(location: Location?): SourcePosition? = getSourcePosition(location)

  // abstract again: the platform's default would call getSourcePositionAsync, which calls back here
  @Throws(NoDataException::class)
  override fun getSourcePosition(location: Location?): SourcePosition?
}
