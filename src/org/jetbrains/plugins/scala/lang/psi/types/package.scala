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

    def conforms(`type`: ScType)
                (implicit typeSystem: TypeSystem): Boolean = {
      conforms(`type`, new ScUndefinedSubstitutor(), checkWeak = false)._1
    }

    def weakConforms(`type`: ScType)
                    (implicit typeSystem: TypeSystem) = {
      conforms(`type`, new ScUndefinedSubstitutor(), checkWeak = true)._1
    }

    def conforms(`type`: ScType,
                 undefinedSubstitutor: ScUndefinedSubstitutor,
                 checkWeak: Boolean = false)
                (implicit typeSystem: TypeSystem): (Boolean, ScUndefinedSubstitutor) = {
      typeSystem.conformance.conformsInner(`type`, scType, substitutor = undefinedSubstitutor, checkWeak = checkWeak)
    }

    def glb(`type`: ScType, checkWeak: Boolean = false)(implicit typeSystem: TypeSystem) = {
      typeSystem.bounds.glb(scType, `type`, checkWeak)
    }

    def lub(`type`: ScType, checkWeak: Boolean = false)(implicit typeSystem: TypeSystem) = {
      typeSystem.bounds.lub(scType, `type`, checkWeak)
    }

    def presentableText = ScType.presentableText(scType)

    def canonicalText = ScType.canonicalText(scType)

    def removeUndefines() = scType.recursiveUpdate {
      case u: ScUndefinedType => (true, Any)
      case tp: ScType => (false, tp)
    }
  }

  implicit class ScTypesExt(val types: Seq[ScType]) extends AnyVal {
    def glb(checkWeak: Boolean = false)(implicit typeSystem: TypeSystem) = {
      typeSystem.bounds.glb(types, checkWeak)
    }

    def lub(checkWeak: Boolean = false)(implicit typeSystem: TypeSystem) = {
      typeSystem.bounds.glb(types, checkWeak)
    }
  }
}
