package org.jetbrains.plugins.scala.lang.psi.api

import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
  * @author adkozlov
  */
package object toplevel {

  implicit class ScNamedElementExt(val namedElement: ScNamedElement) extends AnyVal {
    private def typeParametersOwnerDepth(f: ScTypeParametersOwner, typeDepth: Int): Int = {
      if (f.typeParameters.nonEmpty) {
        (f.typeParameters.map(elemTypeDepth(_)).max + 1).max(typeDepth)
      } else typeDepth
    }

    private def elemTypeDepth(elem: ScNamedElement): Int = {
      elem match {
        case tp: ScTypeParam =>
          val boundsDepth = tp.lowerBound.getOrNothing.typeDepth.max(tp.upperBound.getOrAny.typeDepth)
          typeParametersOwnerDepth(tp, boundsDepth)
        case f: ScFunction =>
          val returnTypeDepth = f.returnType.getOrAny.typeDepth
          typeParametersOwnerDepth(f, returnTypeDepth)
        case ta: ScTypeAliasDefinition =>
          val aliasedDepth = ta.aliasedType(TypingContext.empty).getOrAny.typeDepth
          typeParametersOwnerDepth(ta, aliasedDepth)
        case ta: ScTypeAliasDeclaration =>
          val boundsDepth = ta.lowerBound.getOrNothing.typeDepth.max(ta.upperBound.getOrAny.typeDepth)
          typeParametersOwnerDepth(ta, boundsDepth)
        case t: ScTypedDefinition => t.getType(TypingContext.empty).getOrAny.typeDepth
        case _ => 1
      }
    }
  }

}
