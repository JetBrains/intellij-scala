package org.jetbrains.plugins.scala.lang.psi.stubs


import api.toplevel.templates.ScTemplateParents
import com.intellij.psi.stubs.StubElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

trait ScTemplateParentsStub extends StubElement[ScTemplateParents] {
  def getTemplateParentsTypesTexts: Array[String]
}