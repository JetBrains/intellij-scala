package org.jetbrains.plugins.dotty.lang.psi.stubs.elements

import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ExternalIdOwner

/**
  * @author adkozlov
  */
trait DottyExternalIdOwner extends ExternalIdOwner {
  override def getLanguageName = "dotty"
}
