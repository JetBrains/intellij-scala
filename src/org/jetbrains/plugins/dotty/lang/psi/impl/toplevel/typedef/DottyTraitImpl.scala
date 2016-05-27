package org.jetbrains.plugins.dotty.lang.psi.impl.toplevel.typedef

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTypeDefinitionImpl

/**
  * @author adkozlov
  */
class DottyTraitImpl(node: ASTNode)
  extends ScTypeDefinitionImpl(null, null, node) with ScClass with ScTrait with ScTemplateDefinition {

  override protected def getIconInner = Icons.TRAIT

  override def getSyntheticImplicitMethod = None

  override def getObjectClassOrTraitToken = super.getObjectClassOrTraitToken
}
