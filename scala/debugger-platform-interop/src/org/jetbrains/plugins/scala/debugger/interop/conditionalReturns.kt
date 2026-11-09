@file:JvmName("ConditionalReturns")

package org.jetbrains.plugins.scala.debugger.interop

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.debugger.impl.runBlockingAssertNotInReadAction
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.sun.jdi.Location

/**
 * The position of the `return` keyword when [location] is the return instruction of a conditional (early) return on
 * [lineNumber] of [file], `null` otherwise.
 *
 * Blocks on the platform's suspending [PositionManagerImpl.adjustPositionForConditionalReturnAsync], the same way the
 * platform's [com.intellij.debugger.PositionManagerAsync.getSourcePosition] blocks on `getSourcePositionAsync`.
 */
@Suppress("UnstableApiUsage", "JetBrainsInternalApiUsage")
@RequiresBlockingContext
fun adjustPositionForConditionalReturn(location: Location, file: PsiFile, lineNumber: Int): SourcePosition? =
  runBlockingAssertNotInReadAction {
    PositionManagerImpl.adjustPositionForConditionalReturnAsync(location, file, lineNumber)
  }
