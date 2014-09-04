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
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateParents
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext

/**
 * User: Alexander Podkhalyuzin
 */

class ScTemplateParentsStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement])
        extends StubBaseWrapper[ScTemplateParents](parent, elemType) with ScTemplateParentsStub {
  private var typesString: Array[StringRef] = new Array[StringRef](0)

  private var types: SoftReference[Array[ScTypeElement]] = null
  
  private var constructor: Option[StringRef] = None

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          constructor: Option[String],
          typesString: Array[String]) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.typesString = typesString.map(StringRef.fromString)
    this.constructor = constructor.map(StringRef.fromString)
  }

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          constructor: Option[StringRef],
          typesString: Array[StringRef]) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.typesString = typesString
    this.constructor = constructor
  }

  def getTemplateParentsTypesTexts: Array[String] = typesString.map(StringRef.toString)

  def getConstructor: Option[String] = constructor.map(StringRef.toString)

  def getTemplateParentsTypes: Array[ScType] = {
    if (types != null && types.get != null) 
      return types.get.map((te: ScTypeElement) => te.getType(TypingContext.empty).getOrAny)
    val res: Array[ScTypeElement] = {
      constructor.map(s =>
        ScalaPsiElementFactory.createConstructorTypeElementFromText(StringRef.toString(s), getPsi, null)).toArray ++
      getTemplateParentsTypesTexts.map(ScalaPsiElementFactory.createTypeElementFromText(_, getPsi, null))
    }
    types = new SoftReference[Array[ScTypeElement]](res)
    res.map((te: ScTypeElement) => te.getType(TypingContext.empty).getOrAny)
  }

  def getTemplateParentsTypeElements: Array[ScTypeElement] = {
    if (types != null && types.get != null)
      return types.get
    val res: Array[ScTypeElement] = {
      constructor.map(s =>
        ScalaPsiElementFactory.createConstructorTypeElementFromText(StringRef.toString(s), getPsi, null)).toArray ++
        getTemplateParentsTypesTexts.map(ScalaPsiElementFactory.createTypeElementFromText(_, getPsi, null))
    }
    types = new SoftReference[Array[ScTypeElement]](res)
    res
  }
}