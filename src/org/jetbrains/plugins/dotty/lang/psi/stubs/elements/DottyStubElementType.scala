package org.jetbrains.plugins.dotty.lang.psi.stubs.elements

import org.jetbrains.plugins.scala.lang.psi.stubs.elements.wrappers.ExternalIdOwner

/**
  * @author adkozlov
  */
trait DottyStubElementType extends ExternalIdOwner {
  override def getLanguageName = "dotty"
}
