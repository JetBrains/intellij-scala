package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.SofterReference
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScAnnotationStubImpl.EMPTY_STRING_REF

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.06.2009
 */

class ScAnnotationStubImpl[ParentPsi <: PsiElement](parent : StubElement[ParentPsi],
                                                    elemType : IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                                    name: StringRef = EMPTY_STRING_REF, typeText: StringRef = EMPTY_STRING_REF)
        extends StubBaseWrapper[ScAnnotation](parent, elemType) with ScAnnotationStub {

  private var myTypeElement: SofterReference[ScTypeElement] = null

  def getName: String = StringRef.toString(name)
  def getTypeText: String = StringRef.toString(typeText)
  def getTypeElement: ScTypeElement = {
    if (myTypeElement != null) {
      val typeElement = myTypeElement.get
      if (typeElement != null && (typeElement.getContext eq getPsi)) return typeElement
    }
    val res: ScTypeElement = ScalaPsiElementFactory.createTypeElementFromText(getTypeText, getPsi, null)
    myTypeElement = new SofterReference[ScTypeElement](res)
    res
  }
}

object ScAnnotationStubImpl {
  val EMPTY_STRING_REF = StringRef.fromString("")
}