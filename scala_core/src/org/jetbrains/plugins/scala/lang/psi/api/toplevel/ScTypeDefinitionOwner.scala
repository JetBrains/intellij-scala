package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import psi.api.toplevel.typedef._
import psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.parser._

/**
 * Trait that implements logic by some type definitions aggregation
 *
 * @author ilyas
 */

trait ScTypeDefinitionOwner extends ScalaPsiElement {

  /**
  * @return Type definitions inside current packaging
  */
  def getTypeDefinitions: List[ScTypeDefinition] = {
    val children = childrenOfType[ScTypeDefinition](TokenSets.TMPL_OR_PACKAGING_DEF_BIT_SET)
    (children :\ (Nil: List[ScTypeDefinition]))((y: ScalaPsiElement, x: List[ScTypeDefinition]) =>
      y match {
        case p: ScPackaging => p.getTypeDefinitions ::: x
        case t: ScTypeDefinition => t :: t.getTypeDefinitions ::: x
      })
  }

  def getTypeDefinitionsArray: Array[ScTypeDefinition] = getTypeDefinitions.toArray[ScTypeDefinition]

}