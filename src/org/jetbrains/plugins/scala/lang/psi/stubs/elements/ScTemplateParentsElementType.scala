package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubElement, StubInputStream, StubOutputStream}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScClassParents, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTemplateParentsStubImpl

/**
  * User: Alexander Podkhalyuzin
  * Date: 17.06.2009
  */
abstract class ScTemplateParentsElementType[P <: ScTemplateParents](debugName: String)
  extends ScStubElementType[ScTemplateParentsStub[P], P](debugName) {
  override def serialize(stub: ScTemplateParentsStub[P], dataStream: StubOutputStream): Unit = {
    dataStream.writeNames(stub.parentTypesTexts)
    dataStream.writeOptionName(stub.constructorText)
  }

  override def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]): ScTemplateParentsStub[P] =
    new ScTemplateParentsStubImpl[P](parentStub.asInstanceOf[StubElement[PsiElement]], this,
      parentTypeTextRefs = dataStream.readNames,
      constructorRef = dataStream.readOptionName)

  override def createStub(templateParents: P, parentStub: StubElement[_ <: PsiElement]): ScTemplateParentsStub[P] = {
    val parentsTypesTexts = templateParents.typeElementsWithoutConstructor.toArray.map {
      _.getText
    }

    val constructorText = Option(templateParents).collect {
      case parents: ScClassParents => parents
    }.flatMap {
      _.constructor
    }.map {
      _.getText
    }
    new ScTemplateParentsStubImpl(parentStub, this,
      parentTypeTextRefs = parentsTypesTexts.asReferences,
      constructorRef = constructorText.asReference)
  }
}
