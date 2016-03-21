package org.jetbrains.plugins.scala.lang.psi.types

/**
  * @author adkozlov
  */
package object nonvalue {

  implicit class TypeParametersExt(val typeParameters: Seq[TypeParameter]) extends AnyVal {
    def depth(): Int = 1 + (if (typeParameters.isEmpty) 0
    else {
      typeParameters.map {
        case TypeParameter(_, parameters, lowerType, upperType, _) =>
          lowerType().typeDepth
            .max(upperType().typeDepth)
            .max(parameters.depth())
      }.max
    })
  }

}
