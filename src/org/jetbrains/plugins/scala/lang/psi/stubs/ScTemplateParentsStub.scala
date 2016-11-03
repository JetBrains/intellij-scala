package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.06.2009
  */
trait ScTemplateParentsStub[P <: ScTemplateParents] extends StubElement[P] {
  def parentTypesTexts: Array[String]

  def parentTypeElements: Seq[ScTypeElement]

  def constructorText: Option[String]
}