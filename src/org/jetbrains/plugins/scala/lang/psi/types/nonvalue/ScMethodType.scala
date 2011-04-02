package org.jetbrains.plugins.scala.lang.psi.types.nonvalue

import collection.immutable.HashMap
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.Suspension
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.decompiler.DecompilerUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.psi.{PsiElement, PsiTypeParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import result.TypingContext

/**
 * @author ilyas
 */

/**
 * This is internal type, no expression can have such type.
 */
trait NonValueType extends ScType {
  def isValue = false
}

case class Parameter(name: String, paramType: ScType, isDefault: Boolean, isRepeated: Boolean) {
  def this(param: ScParameter) {
    this(param.name, param.getType(TypingContext.empty).getOrElse(Any), param.isDefaultParam, param.isRepeatedParameter)
  }
}
case class TypeParameter(name: String, lowerType: ScType, upperType: ScType, ptp: PsiTypeParameter)
case class TypeConstructorParameter(name: String, lowerType: ScType, upperType: ScType,
                                    isCovariant: Boolean, ptp: PsiTypeParameter) {
  def isContravariant: Boolean = !isCovariant
}


case class ScMethodType(returnType: ScType, params: Seq[Parameter], isImplicit: Boolean)
                       (val project: Project, val scope: GlobalSearchScope) extends NonValueType {

  def inferValueType: ValueType = {
    return new ScFunctionType(returnType.inferValueType, params.map(_.paramType.inferValueType))(project, scope)
  }

  override def removeAbstracts = new ScMethodType(returnType.removeAbstracts,
    params.map(p => Parameter(p.name, p.paramType.removeAbstracts, p.isDefault, p.isRepeated)), isImplicit)(project, scope)

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = uSubst
    r match {
      case m: ScMethodType => {
        if (m.params.length != params.length) return (false, undefinedSubst)
        var t = Equivalence.equivInner(m.returnType, returnType,undefinedSubst, falseUndef)
        if (!t._1) return (false, undefinedSubst)
        undefinedSubst = t._2
        var i = 0
        while (i < params.length) {
          //todo: Seq[Type] instead of Type*
          if (params(i).isRepeated != m.params(i).isRepeated) return (false, undefinedSubst)
          t = Equivalence.equivInner(params(i).paramType, m.params(i).paramType, undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          i = i + 1
        }
        return (true, undefinedSubst)
      }
      case _ => (false, undefinedSubst)
    }
  }
}

case class ScTypePolymorphicType(internalType: ScType, typeParameters: Seq[TypeParameter]) extends NonValueType {
  if (internalType.isInstanceOf[ScTypePolymorphicType]) {
    throw new IllegalArgumentException("Polymorphic type can't have wrong internal type")
  }

  def polymorphicTypeSubstitutor: ScSubstitutor =
    new ScSubstitutor(new HashMap[(String, String), ScType] ++ (typeParameters.map(tp => ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)),
            if (tp.upperType.equiv(Any)) tp.lowerType else if (tp.lowerType.equiv(Nothing)) tp.upperType else tp.lowerType))),
      Map.empty, None)

  def polymorphicTypeSubstitutorMissedEmptyParams: ScSubstitutor =
    new ScSubstitutor(new HashMap[(String, String), ScType] ++ (typeParameters.flatMap(tp =>
      if (tp.upperType.equiv(Any))
        Seq(((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)), tp.lowerType))
      else if (tp.lowerType.equiv(Nothing))
        Seq(((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)), tp.upperType))
      else Seq.empty
    )),  Map.empty, None)

  def abstractTypeSubstitutor: ScSubstitutor =
    new ScSubstitutor(new HashMap[(String, String), ScType] ++ (typeParameters.map(tp => ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)),
            new ScAbstractType(new ScTypeParameterType(tp.ptp, ScSubstitutor.empty), tp.lowerType, tp.upperType)))), Map.empty, None)

  def typeParameterTypeSubstitutor: ScSubstitutor =
    new ScSubstitutor(new HashMap[(String, String), ScType] ++ (typeParameters.map(tp => ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)),
            new ScTypeParameterType(tp.ptp, ScSubstitutor.empty)))), Map.empty, None)

  def existentialTypeSubstitutor: ScSubstitutor =
    new ScSubstitutor(new HashMap[(String, String), ScType] ++ (typeParameters.map(tp => ((tp.name, ScalaPsiUtil.getPsiElementId(tp.ptp)),
            ScExistentialArgument(tp.name, List.empty, tp.lowerType, tp.upperType)))), Map.empty, None)

  def inferValueType: ValueType = {
    polymorphicTypeSubstitutor.subst(internalType.inferValueType).asInstanceOf[ValueType]
  }

  override def removeAbstracts = ScTypePolymorphicType(internalType.removeAbstracts, typeParameters.map(tp => {
    TypeParameter(tp.name, tp.lowerType.removeAbstracts, tp.upperType.removeAbstracts, tp.ptp)
  }))

  override def equivInner(r: ScType, uSubst: ScUndefinedSubstitutor, falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefinedSubst = uSubst
    r match {
      case p: ScTypePolymorphicType => {
        if (typeParameters.length != p.typeParameters.length) return (false, undefinedSubst)
        var i = 0
        while (i < typeParameters.length) {
          var t = Equivalence.equivInner(typeParameters(i).lowerType, p.typeParameters(i).lowerType, undefinedSubst, falseUndef)
          if (!t._1) return (false,undefinedSubst)
          undefinedSubst = t._2
          t = Equivalence.equivInner(typeParameters(i).upperType, p.typeParameters(i).upperType, undefinedSubst, falseUndef)
          if (!t._1) return (false, undefinedSubst)
          undefinedSubst = t._2
          i = i + 1
        }
        val subst = new ScSubstitutor(new collection.immutable.HashMap[(String, String), ScType] ++
                typeParameters.zip(p.typeParameters).map({
          tuple => ((tuple._1.name, ScalaPsiUtil.getPsiElementId(tuple._1.ptp)), new ScTypeParameterType(tuple._2.name,
            tuple._2.ptp match {
              case p: ScTypeParam => p.typeParameters.toList.map{new ScTypeParameterType(_, ScSubstitutor.empty)}
              case _ => Nil
            }, tuple._2.lowerType, tuple._2.upperType, tuple._2.ptp))
        }), Map.empty, None)
        Equivalence.equivInner(subst.subst(internalType), p.internalType, undefinedSubst, falseUndef)
      }
      case _ => (false, undefinedSubst)
    }
  }
}

/*case class ScTypeConstructorType(internalType: ScType, params: Seq[TypeConstructorParameter]) extends NonValueType {
  def inferValueType: ValueType = {
    //todo: implement
    throw new UnsupportedOperationException("Type Constuctors not implemented yet")
  }
  //todo: equiv
}*/
