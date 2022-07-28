package org.jetbrains.plugins.scala
package codeInsight.unwrap

import com.intellij.codeInsight.unwrap.{UnwrapDescriptorBase, Unwrapper}

class ScalaUnwrapDescriptor extends UnwrapDescriptorBase {
  override def createUnwrappers(): Array[Unwrapper] = Array (
    new ScalaInfixUnwrapper,
    new ScalaMethodCallArgUnwrapper,
    new ScalaTupleUnwrapper,
    new ScalaWhileUnwrapper,
    new ScalaMatchUnwrapper,
    new ScalaCaseClauseRemover,
    new ScalaTryOrFinallyUnwrapper,
    new ScalaTryWithFinallyUnwrapper,
    new ScalaCatchOrFinallyRemover,
    new ScalaForStmtUnwrapper,
    new ScalaIfUnwrapper,
    new ScalaElseUnwrapper,
    new ScalaElseRemover,
    new ScalaBracesUnwrapper,
    new ScalaInterpolatedStringUnwrapper
  )
}
