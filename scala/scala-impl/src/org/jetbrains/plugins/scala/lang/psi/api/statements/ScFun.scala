package org.jetbrains.plugins.scala.lang.psi.api.statements

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}

import scala.collection.Seq

//some functions are not PsiMethods and are e.g. not visible from java
//see ScSyntheticFunction
trait ScFun extends ScTypeParametersOwner {
  def retType: ScType

  def paramClauses: Seq[Seq[Parameter]]

  def methodType: ScType = {
    paramClauses.foldRight[ScType](retType) {
      (params: Seq[Parameter], tp: ScType) => ScMethodType(tp, params, isImplicit = false)
    }
  }

  def polymorphicType: ScType = {
    if (typeParameters.isEmpty) methodType
    else ScTypePolymorphicType(methodType, typeParameters.map(TypeParameter(_)))
  }
}
