package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import api.base.types.ScTypeElement
import api.toplevel.templates.ScTemplateParents
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.io.StringRef
import com.intellij.util.PatchedSoftReference
import psi.impl.ScalaPsiElementFactory
import types.ScType
/**
 * User: Alexander Podkhalyuzin
 * Date: 17.06.2009
 */

class ScTemplateParentsStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
        extends StubBaseWrapper[ScTemplateParents](parent, elemType) with ScTemplateParentsStub {
  private var typesStirng: Array[StringRef] = new Array[StringRef](0)

  private var types: PatchedSoftReference[Array[ScTypeElement]] = null

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          typesString: Array[String]) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.typesStirng = typesString.map(StringRef.fromString(_))
  }

  def getTemplateParentsTypesTexts: Array[String] = typesStirng.map(StringRef.toString(_))


  def getTemplateParentsTypes: Array[ScType] = {
    if (types != null && types.get != null) return types.get.map((te: ScTypeElement) => te.calcType)
    val res: Array[ScTypeElement] = {
      getTemplateParentsTypesTexts.map(ScalaPsiElementFactory.createTypeElementFromText(_, getPsi))
    }
    types = new PatchedSoftReference[Array[ScTypeElement]](res)
    return res.map((te: ScTypeElement) => te.calcType)
  }
}