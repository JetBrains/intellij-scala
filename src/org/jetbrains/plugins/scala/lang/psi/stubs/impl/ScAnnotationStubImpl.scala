package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.reference.SoftReference
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.06.2009
 */

class ScAnnotationStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
        extends StubBaseWrapper[ScAnnotation](parent, elemType) with ScAnnotationStub {
  var name: StringRef = StringRef.fromString("")
  private var typeText: StringRef = _
  private var myTypeElement: SoftReference[ScTypeElement] = null

  def this(parent : StubElement[ParentPsi],
          elemType : IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          name: StringRef, typeText: StringRef) {
    this (parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.name = name
    this.typeText = typeText
  }

  def getName: String = StringRef.toString(name)
  def getTypeText: String = StringRef.toString(typeText)
  def getTypeElement: ScTypeElement = {
    if (myTypeElement != null && myTypeElement.get != null) return myTypeElement.get
    val res: ScTypeElement = {
        ScalaPsiElementFactory.createTypeElementFromText(getTypeText, getPsi, null)
    }
    myTypeElement = new SoftReference[ScTypeElement](res)
    res
  }
}