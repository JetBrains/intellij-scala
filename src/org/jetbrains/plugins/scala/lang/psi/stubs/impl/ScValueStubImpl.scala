package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import api.base.types.ScTypeElement
import api.base.{ScPatternList, ScIdList}
import api.expr.ScExpression
import api.statements.ScValue
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.util.io.StringRef
import com.intellij.util.PatchedSoftReference
import psi.impl.ScalaPsiElementFactory

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.10.2008
 */

class ScValueStubImpl[ParentPsi <: PsiElement](parent: StubElement[ParentPsi],
                                                  elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement])
extends StubBaseWrapper[ScValue](parent, elemType) with ScValueStub {
  private var names: Array[StringRef] = _
  private var declaration: Boolean = false
  private var typeText: StringRef = _
  private var bodyText: StringRef = _
  private var containerText: StringRef = _
  private var myTypeElement: PatchedSoftReference[Option[ScTypeElement]] = null
  private var myBodyExpression: PatchedSoftReference[Option[ScExpression]] = null
  private var myIds: PatchedSoftReference[Option[ScIdList]] = null
  private var myPatterns: PatchedSoftReference[Option[ScPatternList]] = null

  def this(parent: StubElement[ParentPsi],
          elemType: IStubElementType[_ <: StubElement[_], _ <: PsiElement],
          names: Array[String], isDeclaration: Boolean, typeText: String, bodyText: String,
          containerText: String) = {
    this(parent, elemType.asInstanceOf[IStubElementType[StubElement[PsiElement], PsiElement]])
    this.names = for (name <- names) yield StringRef.fromString(name)
    this.declaration = isDeclaration
    this.typeText = StringRef.fromString(typeText)
    this.bodyText = StringRef.fromString(bodyText)
    this.containerText = StringRef.fromString(containerText)
  }

  def getNames: Array[String] = for (name <- names) yield StringRef.toString(name) //todo: remove it if unused

  def isDeclaration = declaration

  def getPatternsContainer: Option[ScPatternList] = {
    if (isDeclaration) return None
    if (myPatterns != null && myPatterns.get != null) return myPatterns.get
    val res: Option[ScPatternList] = {
      if (getBindingsContainerText != "") {
        Some(ScalaPsiElementFactory.createPatterListFromText(getBindingsContainerText, getPsi))
      } else None
    }
    myPatterns = new PatchedSoftReference[Option[ScPatternList]](res)
    res
  }

  def getTypeText: String = StringRef.toString(typeText)

  def getBodyExpr: Option[ScExpression] = {
    if (myBodyExpression != null && myBodyExpression.get != null) return myBodyExpression.get
    val res: Option[ScExpression] = {
      if (getBodyText != "") {
        Some(ScalaPsiElementFactory.createExpressionWithContextFromText(getBodyText, getPsi))
      }
      else {
        None
      }
    }
    myBodyExpression = new PatchedSoftReference[Option[ScExpression]](res)
    res
  }

  def getTypeElement: Option[ScTypeElement] = {
    if (myTypeElement != null && myTypeElement.get != null) return myTypeElement.get
    val res: Option[ScTypeElement] = {
      if (getTypeText != "") {
        Some(ScalaPsiElementFactory.createTypeElementFromText(getTypeText, getPsi))
      }
      else None
    }
    myTypeElement = new PatchedSoftReference[Option[ScTypeElement]](res)
    res
  }

  def getIdsContainer: Option[ScIdList] = {
    if (!isDeclaration) return None
    if (myIds != null && myIds.get != null) return myIds.get
    val res: Option[ScIdList] = {
      if (getBindingsContainerText != "") {
        Some(ScalaPsiElementFactory.createIdsListFromText(getBindingsContainerText, getPsi))
      } else None
    }
    myIds = new PatchedSoftReference[Option[ScIdList]](res)
    res
  }

  def getBodyText: String = StringRef.toString(bodyText)

  def getBindingsContainerText: String = StringRef.toString(containerText)
}