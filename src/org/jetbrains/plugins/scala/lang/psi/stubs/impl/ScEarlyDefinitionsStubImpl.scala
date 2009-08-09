package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import api.toplevel.ScEarlyDefinitions
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScEarlyDefinitionsStubImpl [ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
        extends StubBaseWrapper[ScEarlyDefinitions](parent, elemType) with ScEarlyDefinitionsStub {

}