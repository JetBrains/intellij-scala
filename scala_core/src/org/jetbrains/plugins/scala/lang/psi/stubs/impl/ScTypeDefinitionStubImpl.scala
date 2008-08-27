package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.util.io.StringRef
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType, StubBase}
import elements.wrappers.StubElementWrapper
import api.toplevel.typedef.ScTypeDefinition

/**
 * @author ilyas
 */

class ScTypeDefinitionStubImpl(parent: StubElement[_ <: PsiElement],
                              elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
extends StubBaseWrapper[ScTypeDefinition](parent, elemType) with ScTypeDefinitionStub {

  type ElemType = IStubElementType[StubElement[PsiElement], PsiElement]
  type ParentType = StubElement[_ <: PsiElement]

  var myName: StringRef = _
  var myQualName: StringRef = _
  var mySourceFileName: StringRef = _

  def this(parent: StubElement[_ <: PsiElement],
          elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement],
          name: String,
          qualName: String,
          sourceFileName: String) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    mySourceFileName = StringRef.fromString(sourceFileName)
    myName = StringRef.fromString(name)
    myQualName = StringRef.fromString(qualName)
  }

  def sourceFileName = mySourceFileName

  def qualName = myQualName

  def getName = myName


}