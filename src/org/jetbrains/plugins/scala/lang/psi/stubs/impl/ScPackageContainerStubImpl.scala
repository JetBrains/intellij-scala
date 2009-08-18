package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import api.toplevel.packaging.ScPackageContainer
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.util.io.StringRef
import com.intellij.psi.{PsiClass, PsiElement}
import collection.mutable.ArrayBuffer
import com.intellij.psi.tree.TokenSet

/**
 * @author ilyas
 */

class ScPackageContainerStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
extends StubBaseWrapper[ScPackageContainer](parent, elemType) with ScPackageContainerStub {

  var myPrefix : StringRef = _
  var myOwnNamePart : StringRef = _

  def this(parent : StubElement[ParentPsi],
          elemType : IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          prefix : String,
          ownNamePart : String) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    myPrefix = StringRef.fromString(prefix)
    myOwnNamePart = StringRef.fromString(ownNamePart)
  }

  def prefix = StringRef.toString(myPrefix)
  def ownNamePart = StringRef.toString(myOwnNamePart)
}