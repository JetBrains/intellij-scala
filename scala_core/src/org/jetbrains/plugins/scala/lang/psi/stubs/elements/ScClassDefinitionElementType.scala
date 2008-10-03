package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.compiled.ScClsTypeDefinitionImpl
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScClassImpl
import api.toplevel.typedef.{ScClass, ScTypeDefinition}
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

/**
 * @author ilyas
 */

class ScClassDefinitionElementType extends ScTypeDefinitionElementType[ScClass]("class definition") {

  def createElement(node: ASTNode): PsiElement = new ScClassImpl(node)

  def createPsi(stub: ScTypeDefinitionStub) = /*todo[8858] if (isCompiled(stub))
    new ScClsTypeDefinitionImpl(stub) else
    */new ScClassImpl(stub)

}
