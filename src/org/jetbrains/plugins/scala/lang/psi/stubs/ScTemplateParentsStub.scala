package org.jetbrains.plugins.scala
package lang
package psi
package stubs


import api.toplevel.templates.ScTemplateParents
import com.intellij.psi.stubs.StubElement
import types.ScType

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

trait ScTemplateParentsStub extends StubElement[ScTemplateParents] {
  def getTemplateParentsTypesTexts: Array[String]

  def getTemplateParentsTypes: Array[ScType]
}