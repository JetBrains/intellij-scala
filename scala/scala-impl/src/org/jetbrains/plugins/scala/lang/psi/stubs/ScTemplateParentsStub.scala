package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.stubs.StubElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScConstructorInvocation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents

trait ScTemplateParentsStub extends StubElement[ScTemplateParents] {
  def parentClausesText: Array[String]

  def parentClauses: Seq[ScConstructorInvocation]

  def supersText: String = parentClausesText.mkString(" with ")
}