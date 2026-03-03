package org.jetbrains.plugins.scala.lang.scaladoc.psi.api

import org.jetbrains.plugins.scala.lang.scaladoc.reflinks.psi.ScDocRefQuery

trait ScDocThrowTagValue extends ScDocTagValue {
  final def query: ScDocRefQuery = referenceLink.query
  def referenceLink: ScDocReferenceLink
}
