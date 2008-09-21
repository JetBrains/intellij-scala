package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import api.toplevel.packaging.ScPackageContainer
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.util.io.StringRef

/**
 * @author ilyas
 */

class ScPackageContainerStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                          elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
extends StubBaseWrapper[ScPackageContainer](parent, elemType) with ScPackageContainerStub {

  var myPrefix : StringRef = _
  var myOwnNamePart : StringRef = _

  def this(parent : StubElement[ParentPsi],
          elemType : IStubElementType[_ <: StubElement[_], _ <: PsiElement],
          prefix : String,
          ownNamePart : String) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    myPrefix = StringRef.fromString(prefix)
    myOwnNamePart = StringRef.fromString(ownNamePart)
  }

  def prefix = StringRef.toString(myPrefix)
  def ownNamePart = StringRef.toString(myOwnNamePart)

}