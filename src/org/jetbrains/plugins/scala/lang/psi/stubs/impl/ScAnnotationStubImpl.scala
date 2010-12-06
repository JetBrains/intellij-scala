package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import api.expr.ScAnnotation
import api.toplevel.ScEarlyDefinitions
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import api.base.ScStableCodeReferenceElement
import com.intellij.util.io.StringRef
import com.intellij.util.PatchedSoftReference

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.06.2009
 */

class ScAnnotationStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
        extends StubBaseWrapper[ScAnnotation](parent, elemType) with ScAnnotationStub {
  var name: StringRef = StringRef.fromString("")

  def this(parent : StubElement[ParentPsi],
          elemType : IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement], name: StringRef) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = name
  }

  def getName: String = StringRef.toString(name)
}