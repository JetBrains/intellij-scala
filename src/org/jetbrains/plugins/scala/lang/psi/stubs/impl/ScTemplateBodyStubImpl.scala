package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScTemplateBodyStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
  extends StubBaseWrapper[ScTemplateBody](parent, elemType) with ScTemplateBodyStub {

}