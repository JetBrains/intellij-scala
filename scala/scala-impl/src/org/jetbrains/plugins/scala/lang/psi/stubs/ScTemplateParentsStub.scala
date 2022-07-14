package org.jetbrains.plugins.scala
package lang
package psi
package stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.06.2009
  */
trait ScTemplateParentsStub extends StubElement[ScTemplateParents] {
  def parentClausesText: Array[String]

  def parentClauses: Seq[ScConstructorInvocation]

  def supersText: String = parentClausesText.mkString(" with ")
}