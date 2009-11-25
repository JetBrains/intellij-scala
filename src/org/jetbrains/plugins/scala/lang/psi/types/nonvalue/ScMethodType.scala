package org.jetbrains.plugins.scala.lang.psi.types.nonvalue

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import collection.immutable.HashMap
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.Suspension
import com.intellij.psi.PsiTypeParameter

/**
 * @author ilyas
 */

/**
 * This is internal type, no expression can have such type.
 */
trait NonValueType extends ScType {
  def isValue = false
}

case class Parameter(name: String, paramType: ScType, isDefault: Boolean, isRepeated: Boolean)
case class TypeParameter(name: String, lowerType: ScType, upperType: ScType, ptp: PsiTypeParameter)
case class TypeConstructorParameter(name: String, lowerType: ScType, upperType: ScType,
                                    isCovariant: Boolean, ptp: PsiTypeParameter) {
  def isContravariant: Boolean = !isCovariant
}


case class ScMethodType(returnType: ScType, params: Seq[Parameter], isImplicit: Boolean) extends NonValueType {
  def inferValueType: ValueType = {
    return ScFunctionType(returnType.inferValueType, params.map(_.paramType.inferValueType))
  }

  override def equiv(t: ScType): Boolean = {
    t match {
      case m: ScMethodType => {
        if (m.params.length != params.length) return false
        if (!m.returnType.equiv(returnType)) return false
        for (i <- 0 until params.length) {
          if (params(i).isRepeated != m.params(i).isRepeated) return false //todo: Seq[Type] instead of Type*
          if (!params(i).paramType.equiv(m.params(i).paramType)) return false
        }
        return true
      }
      case _ => false
    }
  }
}

case class ScTypePolymorphicType(internalType: ScType, typeParameters: Seq[TypeParameter]) extends NonValueType {
  if (internalType.isInstanceOf[ScTypeConstructorType] ||
      internalType.isInstanceOf[ScTypePolymorphicType]) {
    throw new IllegalArgumentException("Polymorphic type can't have wrong internal type")
  }

  def inferValueType: ValueType = {
    val subst = new ScSubstitutor(new HashMap[String, ScType] ++ (typeParameters.map(tp => (tp.name, tp.lowerType))), Map.empty, Map.empty)
    subst.subst(internalType.inferValueType).asInstanceOf[ValueType]
  }

  override def equiv(t: ScType): Boolean = {
    t match {
      case p: ScTypePolymorphicType => {
        if (typeParameters.length != p.typeParameters.length) return false
        for (i <- 0 until typeParameters.length) {
          if (!typeParameters(i).lowerType.equiv(p.typeParameters(i).lowerType)) return false
          if (!typeParameters(i).upperType.equiv(p.typeParameters(i).upperType)) return false
        }
        import Suspension._
        val subst = new ScSubstitutor(new HashMap[String, ScType] ++ typeParameters.zip(p.typeParameters).map({
          tuple => (tuple._1.name, new ScTypeParameterType(tuple._2.name, List.empty, tuple._2.lowerType, tuple._2.upperType, tuple._2.ptp))
        }), Map.empty, Map.empty)
        subst.subst(internalType).equiv(p.internalType)
      }
      case _ => false
    }
  }
}

case class ScTypeConstructorType(internalType: ScType, params: Seq[TypeConstructorParameter]) extends NonValueType {
  def inferValueType: ValueType = {
    //todo: understand it and implement
    throw new UnsupportedOperationException("Type Constuctors not implemented yet")
  }
  //todo: equiv
}
