package org.jetbrains.plugins.scala.lang.psi.api.base.types.cc

import org.jetbrains.plugins.scala.lang.lexer.{ScalaTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

trait ScCaptureRef extends ScalaPsiElement {
  def captureRef: Option[ScReference] = findChild[ScReference]
  def hasCapabilityReach: Boolean = findFirstChildByType(ScalaTokenType.ReachCapabilityStar).isDefined
  def captureFilter: Option[ScCaptureFilter] = findLastChildByTypeScala[ScCaptureFilter](ScalaElementType.CAPTURE_FILTER)
  def isReadOnlyCapability: Boolean = findFirstChildByType(ScalaTokenType.ReadOnlyCapabilityKeyword).isDefined
}
