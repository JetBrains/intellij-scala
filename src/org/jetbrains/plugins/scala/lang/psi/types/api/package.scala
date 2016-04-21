package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiTypeParameter
import org.jetbrains.plugins.scala.Suspension

/**
  * @author adkozlov
  */
package object api {

  private val EMPTY_ARRAY: Array[TypeParameter] = Array.empty

  implicit class TypeParameterExt(val typeParameter: TypeParameter) extends AnyVal {
    def toType: TypeParameterType = {
      def lift(function: () => ScType) = new Suspension[ScType](function())

      val TypeParameter(name, typeParams, lowerType, upperType, psiTypeParameter) = typeParameter
      TypeParameterType(name, typeParams.map(_.toType).toList, lift(lowerType), lift(upperType), psiTypeParameter)
    }
  }

  implicit class TypeParametersExt(val typeParameters: Array[TypeParameter]) extends AnyVal {
    def subst(function: TypeParameter => TypeParameter): Array[TypeParameter] = typeParameters match {
      case Array() => EMPTY_ARRAY
      case array => array.map(function)
    }

    def depth: Int = 1 + (typeParameters match {
      case Array() => 0
      case seq => seq.map {
        case TypeParameter(_, parameters, lowerType, upperType, _) =>
          lowerType().typeDepth
            .max(upperType().typeDepth)
            .max(parameters.toArray.depth)
      }.max
    })
  }

  implicit class PsiTypeParamatersExt(val typeParameters: Array[PsiTypeParameter]) extends AnyVal {
    def instantiate: Array[TypeParameter] = typeParameters match {
      case Array() => EMPTY_ARRAY
      case array => array.map(TypeParameter(_))
    }
  }

}
