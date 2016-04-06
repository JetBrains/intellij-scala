package org.jetbrains.plugins.scala
package format

import java.util.{Calendar, Date}

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.{ScDesignatorType, ScType, api}

/**
 * Pavel Fatin
 */

object Types {
  def valueOf(aType: ScType): Any = aType match {
    case Boolean => true
    case api.Byte => 0.toByte
    case Char => 'c'
    case api.Short => 0.toShort
    case Int => 0
    case Long => 0L
    case Float => 0.0F
    case api.Double => 0D
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
