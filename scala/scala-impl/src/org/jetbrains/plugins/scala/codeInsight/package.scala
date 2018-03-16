package org.jetbrains.plugins.scala

import org.jetbrains.plugins.scala.lang.psi.types.ScCompoundType

package object codeInsight {

  object CaseClassType {

    private[this] val SyntheticSupers = Set(
      "_root_.scala.Product",
      "_root_.scala.Serializable",
      "_root_.java.lang.Object"
    )

    def unapply(compoundType: ScCompoundType): Option[ScCompoundType] = Some {
      val filteredComponents = compoundType.components.filterNot { tp =>
        SyntheticSupers(tp.canonicalText)
      }
      compoundType.copy(components = filteredComponents)(compoundType.projectContext)
    }
  }

}
