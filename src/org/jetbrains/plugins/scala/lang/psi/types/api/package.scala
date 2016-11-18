package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiTypeParameter

/**
  * @author adkozlov
  */
package object api {

  implicit class TypeParametersArrayExt(val typeParameters: Array[TypeParameter]) extends AnyVal {
    def subst(function: TypeParameter => TypeParameter): Seq[TypeParameter] = typeParameters.toSeq.subst(function)

    def depth = typeParameters.toSeq.depth
  }

  implicit class TypeParametersSeqExt(val typeParameters: Seq[TypeParameter]) extends AnyVal {
    def subst(function: TypeParameter => TypeParameter): Seq[TypeParameter] = typeParameters.map(function)

    def depth: Int = {
      def depth(tp: TypeParameter): Int = Seq(tp.lowerType.v.typeDepth, tp.upperType.v.typeDepth, tp.typeParameters.depth).max

      val maxDepth = if (typeParameters.isEmpty) 0 else typeParameters.map(depth).max
      1 + maxDepth
    }
  }

  implicit class PsiTypeParamatersExt(val typeParameters: Array[PsiTypeParameter]) extends AnyVal {
    def instantiate: Seq[TypeParameter] = typeParameters match {
      case Array() => Seq.empty
      case array => array.toSeq.map(TypeParameter(_))
    }
  }

}
