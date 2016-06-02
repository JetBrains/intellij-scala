package org.jetbrains.plugins.dotty.lang.psi.impl.toplevel.typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.dotty.lang.parser.DottyElementTypes
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTypeDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub

/**
  * @author adkozlov
  */
class DottyTraitImpl private(stub: StubElement[ScTemplateDefinition], nodeType: IElementType, node: ASTNode)
  extends ScTypeDefinitionImpl(stub, nodeType, node) with ScClass with ScTrait with ScTemplateDefinition {

  def this(node: ASTNode) = this(null, null, node)

  def this(stub: ScTemplateDefinitionStub) = this(stub, DottyElementTypes.traitDefinition, null)

  override protected def getIconInner = Icons.TRAIT

  override def getSyntheticImplicitMethod = None

  override def getObjectClassOrTraitToken = super.getObjectClassOrTraitToken
}
