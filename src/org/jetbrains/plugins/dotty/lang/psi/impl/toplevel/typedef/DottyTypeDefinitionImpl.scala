package org.jetbrains.plugins.dotty.lang.psi.impl.toplevel.typedef

import javax.swing.Icon

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTypeDefinitionImpl

/**
  * @author adkozlov
  */
class DottyTypeDefinitionImpl(node: ASTNode)
  extends ScTypeDefinitionImpl(null, node.getElementType, node) with ScClass with ScTrait with ScTemplateDefinition {
  override protected def getIconInner: Icon = ???

  override def getSyntheticImplicitMethod: Option[ScFunction] = ???

  override def constructor: Option[ScPrimaryConstructor] = ???

  override def fakeCompanionClass: PsiClass = ???

  override def parameters: Seq[ScParameter] = ???
}
