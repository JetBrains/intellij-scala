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
  def getTemplateParentsTypeElements: Array[ScTypeElement]

  def getTemplateParentsTypesTexts: Array[String]

  def getTemplateParentsTypes: Array[ScType]

  def getConstructor: Option[String]
}