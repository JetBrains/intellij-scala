package org.jetbrains.plugins.scala.lang.psi

import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem

/**
  * @author adkozlov
  */
package object types {

  implicit class ScTypeExt(val scType: ScType) extends AnyVal {
    def equiv(`type`: ScType)(implicit typeSystem: TypeSystem): Boolean = {
      typeSystem.equivalence.equiv(scType, `type`)
    }

    def equiv(`type`: ScType, undefinedSubstitutor: ScUndefinedSubstitutor, falseUndef: Boolean = true)
             (implicit typeSystem: TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
      typeSystem.equivalence.equivInner(scType, `type`, undefinedSubstitutor, falseUndef)
    }

    /**
      * Checks, whether the following assignment is correct:
      * val x: type = (y: this)
      */
    def conforms(`type`: ScType, checkWeak: Boolean = false) = Conformance.conforms(`type`, scType, checkWeak)

    def weakConforms(`type`: ScType) = conforms(`type`, checkWeak = true)

    def presentableText = ScType.presentableText(scType)

    def canonicalText = ScType.canonicalText(scType)

    def removeUndefines() = scType.recursiveUpdate {
      case u: ScUndefinedType => (true, Any)
      case tp: ScType => (false, tp)
    }
  }

}
