package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.Suspension
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType

/**
  * @author adkozlov
  */
package object nonvalue {

  implicit class TypeParameterExt(val typeParameter: TypeParameter) extends AnyVal {
    def toType: TypeParameterType = {
      def lift(function: () => ScType) = new Suspension[ScType](function())

      val TypeParameter(name, typeParams, lowerType, upperType, psiTypeParameter) = typeParameter
      TypeParameterType(name, typeParams.map(_.toType).toList, lift(lowerType), lift(upperType), psiTypeParameter)
    }
  }

  implicit class TypeParametersExt(val typeParameters: Seq[TypeParameter]) extends AnyVal {
    def depth: Int = 1 + (if (typeParameters.isEmpty) 0
    else {
      typeParameters.map {
        case TypeParameter(_, parameters, lowerType, upperType, _) =>
          lowerType().typeDepth
            .max(upperType().typeDepth)
            .max(parameters.depth)
      }.max
    })
  }

}
