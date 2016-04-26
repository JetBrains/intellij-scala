package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiTypeParameter

/**
  * @author adkozlov
  */
package object api {

  private val EMPTY_ARRAY: Array[TypeParameter] = Array.empty

  implicit class TypeParametersExt(val typeParameters: Array[TypeParameter]) extends AnyVal {
    def subst(function: TypeParameter => TypeParameter): Array[TypeParameter] = typeParameters match {
      case Array() => EMPTY_ARRAY
      case array => array.map(function)
    }

    def depth: Int = 1 + (typeParameters match {
      case Array() => 0
      case seq => seq.map {
        case TypeParameter(parameters, lowerType, upperType, _) =>
          lowerType.v.typeDepth
            .max(upperType.v.typeDepth)
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
