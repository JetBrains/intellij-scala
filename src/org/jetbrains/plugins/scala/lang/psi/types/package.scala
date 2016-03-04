package org.jetbrains.plugins.scala.lang.psi

/**
  * @author adkozlov
  */
package object types {

  implicit class ScTypeExt(val scType: ScType) extends AnyVal {
    def equiv(`type`: ScType) = Equivalence.equiv(scType, `type`)

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
