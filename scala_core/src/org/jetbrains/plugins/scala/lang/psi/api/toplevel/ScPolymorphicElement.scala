package org.jetbrains.plugins.scala.lang.psi.api.toplevel

/**
 * @author ven
 */
trait ScPolymorphicElement extends ScTypeParametersOwner with ScTypeBoundsOwner with ScNamedElement {

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset
}