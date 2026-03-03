package org.jetbrains.plugins.scala.lang.scaladoc.psi.api

import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.ScDocRefQuery

/**
 * This is a wrapper around the actual reference
 */
trait ScDocReferenceLink extends ScalaPsiElement {
  def query: ScDocRefQuery
}
