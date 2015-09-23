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
  private var typesString: Seq[StringRef] = Seq.empty
  private var types: SofterReference[Seq[ScTypeElement]] = null
  private var constructor: Option[StringRef] = None

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
          constructor: Option[StringRef],
          typesString: Seq[StringRef]) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.typesString = typesString
    this.constructor = constructor
  }

  def getTemplateParentsTypesTexts: Seq[String] = typesString.map(StringRef.toString)

  def getConstructor: Option[String] = constructor.map(StringRef.toString)

  def getTemplateParentsTypes: Seq[ScType] =
    getTemplateParentsTypeElements.map(_.getType(TypingContext.empty).getOrAny)

  def getTemplateParentsTypeElements: Seq[ScTypeElement] = {
    if (types != null) {
      val typeElements = types.get
      if (typeElements != null && typeElements.forall { elem =>
        val context = elem.getContext
        context.eq(getPsi) || (context.getContext != null && context.getContext.eq(getPsi))
      }) return typeElements
    }
    val res: Seq[ScTypeElement] =
      constructor.map(s =>
        ScalaPsiElementFactory.createConstructorTypeElementFromText(StringRef.toString(s), getPsi, null)).toSeq ++
        getTemplateParentsTypesTexts.map(ScalaPsiElementFactory.createTypeElementFromText(_, getPsi, null))
    types = new SofterReference(res)
    res
  }
}