package org.jetbrains.plugins.scala.lang.dfa.types

import com.intellij.codeInspection.dataFlow.types.{DfConstantType, DfType}

case object DfUnitType extends DfConstantType {
  override def join(dfType: DfType): DfType =
    if (dfType == this) this else DfType.TOP

  override def tryJoinExactly(dfType: DfType): DfType = join(dfType)
}
