package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTraitParents
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScTraitParentsImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.06.2009
  */
class ScTraitParentsElementType extends ScTemplateParentsElementType[ScTraitParents]("trait parents") {
  override def createElement(node: ASTNode): ScTraitParents = new ScTraitParentsImpl(node)

  override def createPsi(stub: ScTemplateParentsStub[ScTraitParents]): ScTraitParents = new ScTraitParentsImpl(stub)

  override def isLeftBound = true
}