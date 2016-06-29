package org.jetbrains.plugins.scala.lang.psi.types.api

import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.NonValueType
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScUndefinedSubstitutor}

/**
  * Use this type if you want to resolve generics.
  * In conformance using ScUndefinedSubstitutor you can accumulate information
  * about possible generic type.
  */
case class UndefinedType(parameterType: TypeParameterType, var level: Int = 0)
                        (implicit val typeSystem: TypeSystem)
  extends NonValueType with TypeInTypeSystem {

  override def visitType(visitor: TypeVisitor): Unit = visitor.visitUndefinedType(this)

  def inferValueType: TypeParameterType = parameterType

  override def equivInner(`type`: ScType, substitutor: ScUndefinedSubstitutor, falseUndef: Boolean)
                         (implicit typeSystem: TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
    val result = `type` match {
      case _ if falseUndef => substitutor
      case UndefinedType(_, thatLevel) if thatLevel == level => substitutor
      case UndefinedType(thatParameterType, thatLevel) if thatLevel > level =>
        substitutor.addUpper(thatParameterType.nameAndId, this)
      case that: UndefinedType if that.level < level =>
        substitutor.addUpper(parameterType.nameAndId, that)
      case that =>
        val name = parameterType.nameAndId
        substitutor.addLower(name, that).addUpper(name, that)
    }

    (!falseUndef, result)
  }
}
