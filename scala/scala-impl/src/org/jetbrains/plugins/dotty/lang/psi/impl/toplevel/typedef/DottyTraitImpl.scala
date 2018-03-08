package org.jetbrains.plugins.dotty.lang.psi.impl.toplevel.typedef

import javax.swing.Icon

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScDecoratedIconOwner}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTypeDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub

/**
  * @author adkozlov
  */
class DottyTraitImpl private(stub: ScTemplateDefinitionStub, node: ASTNode)
  extends ScTypeDefinitionImpl(stub, ScalaElementTypes.TRAIT_DEFINITION, node) with ScClass with ScTrait with ScDecoratedIconOwner {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScTemplateDefinitionStub) = this(stub, null)


  override def getObjectClassOrTraitToken: PsiElement = super.getObjectClassOrTraitToken

  override protected def getBaseIcon(flags: Int): Icon = Icons.TRAIT

  override def getSyntheticImplicitMethod: Option[ScFunction] = None
}
