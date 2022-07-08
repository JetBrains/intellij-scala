package org.jetbrains.plugins.scala
package format

import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}

import java.{lang, util}

object Types {
  def valueOf(aType: ScType): Any = {
    val stdTypes = aType.projectContext.stdTypes
    import stdTypes._

    aType match {
      case Null => null
      case Boolean => true
      case Byte => 0.toByte
      case Char => 'c'
      case Short => 0.toShort
      case Int => 0
      case Long => 0L
      case Float => 0.0F
      case Double => 0D
      case ScLiteralType(value, _) => value.value
      case ScDesignatorType(element) => element.name match {
        case "String" => ""
        case "Boolean" => lang.Boolean.valueOf(true)
        case "Byte" => lang.Byte.valueOf(0.toByte)
        case "Character" => lang.Character.valueOf('c')
        case "Short" => lang.Short.valueOf(0.toShort)
        case "Integer" => lang.Integer.valueOf(0)
        case "Long" => lang.Long.valueOf(0L)
        case "Float" => lang.Float.valueOf(0.0F)
        case "Double" => lang.Double.valueOf(0D)
        case "BigInt" | "BigInteger" => BigInt(0)
        case "BigDecimal" => BigDecimal(0)
        case "Calendar" => util.Calendar.getInstance
        case "Date" => new util.Date()
        case _ => new Object()
      }
      case _ => new Object()
    }
  }
}
