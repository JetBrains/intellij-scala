package org.jetbrains.plugins.scala
package conversion
package copy

import org.jetbrains.plugins.scala.lang.refactoring.AssociationsData

/**
  * Pavel Fatin
  */
case class Associations(override val associations: Array[AssociationsData.Association])
  extends AssociationsData(associations, Associations)
    with Cloneable {

  override def clone(): Associations = copy()
}

object Associations extends AssociationsData.Companion(classOf[Associations], "ScalaReferenceData")