package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.{ScEnumBodyImpl, ScTemplateBodyImpl}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTemplateBodyStubImpl

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */
sealed abstract class ScTemplateBodyElementType(debugName: String) extends ScStubElementType[ScTemplateBodyStub, ScTemplateBody](debugName) {

  override final def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]) =
    new ScTemplateBodyStubImpl(parentStub, this)

  override final def createStubImpl(templateBody: ScTemplateBody, parentStub: StubElement[_ <: PsiElement]) =
    new ScTemplateBodyStubImpl(parentStub, this)
}

object TemplateBody extends ScTemplateBodyElementType("template body") {

  override def createElement(node: ASTNode) = new ScTemplateBodyImpl(null, null, node)

  override def createPsi(stub: ScTemplateBodyStub) = new ScTemplateBodyImpl(stub, this, null)
}

object EnumBody extends ScTemplateBodyElementType("enum body") {

  override def createElement(node: ASTNode) = new ScEnumBodyImpl(null, null, node)

  override def createPsi(stub: ScTemplateBodyStub) = new ScEnumBodyImpl(stub, this, null)
}