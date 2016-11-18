package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl


import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable

/**
  * User: Alexander Podkhalyuzin
  * Date: 18.10.2008
  */
class ScVariableStubImpl(parent: StubElement[_ <: PsiElement],
                         elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                         override val isDeclaration: Boolean,
                         namesRefs: Array[StringRef],
                         typeTextRef: Option[StringRef],
                         bodyTextRef: Option[StringRef],
                         containerTextRef: Option[StringRef],
                         override val isLocal: Boolean)
  extends ScValueOrVariableStubImpl[ScVariable](parent = parent, elementType = elementType,
      isDeclaration = isDeclaration,
      namesRefs = namesRefs,
      typeTextRef = typeTextRef,
      bodyTextRef = bodyTextRef,
      containerTextRef = containerTextRef,
      isLocal = isLocal) with ScVariableStub