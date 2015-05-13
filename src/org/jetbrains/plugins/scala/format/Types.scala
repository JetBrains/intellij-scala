package org.jetbrains.plugins.scala
package format

import java.util.{Calendar, Date}

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.types
import org.jetbrains.plugins.scala.lang.psi.types.{ScDesignatorType, ScType}

/**
 * Pavel Fatin
 */

object Types {
  def valueOf(aType: ScType): Any = aType match {
    case types.Boolean => true
    case types.Byte => 0.toByte
    case types.Char => 'c'
    case types.Short => 0.toShort
    case types.Int => 0
    case types.Long => 0L
    case types.Float => 0.0F
    case types.Double => 0D
    case ScDesignatorType(element) => element.name match {
      case "String" => ""
      case "BigInt" | "BigInteger" => BigInt(0)
      case "BigDecimal" => BigDecimal(0)
      case "Calendar" => Calendar.getInstance
      case "Date" => new Date()
      case _ => new Object()
    }
    case _ => new Object()
  }
}
