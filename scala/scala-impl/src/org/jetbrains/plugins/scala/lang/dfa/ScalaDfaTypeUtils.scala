package org.jetbrains.plugins.scala.lang.dfa

import com.intellij.codeInspection.dataFlow.types.{DfType, DfTypes}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.literals._

object ScalaDfaTypeUtils {

  def literalToDfType(literal: ScLiteral): DfType = literal match {
    case ScNullLiteral(_) => DfTypes.NULL
    case int: ScIntegerLiteral => DfTypes.intValue(int.getValue)
    case long: ScLongLiteral => DfTypes.longValue(long.getValue)
    case float: ScFloatLiteral => DfTypes.floatValue(float.getValue)
    case double: ScDoubleLiteral => DfTypes.doubleValue(double.getValue)
    case boolean: ScBooleanLiteral => DfTypes.booleanValue(boolean.getValue)
    case char: ScCharLiteral => DfTypes.intValue(char.getValue.toInt)
    case _ => DfType.TOP
  }
}
