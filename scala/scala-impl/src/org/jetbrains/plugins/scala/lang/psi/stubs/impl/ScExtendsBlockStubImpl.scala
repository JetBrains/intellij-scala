package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.StringRefArrayExt

/**
  * @author ilyas
  */
class ScExtendsBlockStubImpl(parent: StubElement[_ <: PsiElement],
                             elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                             private val baseClassesRefs: Array[StringRef])
  extends StubBase[ScExtendsBlock](parent, elementType) with ScExtendsBlockStub {

  def baseClasses: Array[String] = baseClassesRefs.asStrings
}