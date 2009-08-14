package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import api.expr.ScAnnotation
import api.toplevel.ScEarlyDefinitions
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
/**
 * User: Alexander Podkhalyuzin
 * Date: 22.06.2009
 */

class ScAnnotationStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
        extends StubBaseWrapper[ScAnnotation](parent, elemType) with ScAnnotationStub {

}