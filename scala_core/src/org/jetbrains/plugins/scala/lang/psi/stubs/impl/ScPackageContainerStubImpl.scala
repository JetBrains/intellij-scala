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

  var myQualName: StringRef = _

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement],
          qualName: String) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    myQualName = StringRef.fromString(qualName)
  }

  def fqn = StringRef.toString(myQualName)

}