package org.jetbrains.plugins.scala
package lang
package psi
package stubs


import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

trait ScTemplateParentsStub extends StubElement[ScTemplateParents] {
  def getTemplateParentsTypeElements: Seq[ScTypeElement]

  def getTemplateParentsTypesTexts: Seq[String]

  def getTemplateParentsTypes: Seq[ScType]

  def getConstructor: Option[String]
}