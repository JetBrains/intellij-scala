package org.jetbrains.plugins.scala
package lang
package psi
package stubs


import api.toplevel.templates.ScTemplateParents
import com.intellij.psi.stubs.StubElement
import types.ScType
import api.base.types.ScTypeElement

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