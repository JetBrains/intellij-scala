package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

/**
  * User: Alexander Podkhalyuzin
  * Date: 19.10.2008
  */
class ScParameterStubImpl(parent: StubElement[_ <: PsiElement],
                          elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                          name: String,
                          val typeText: Option[String],
                          val isStable: Boolean,
                          val isDefaultParameter: Boolean,
                          val isRepeated: Boolean,
                          val isVal: Boolean,
                          val isVar: Boolean,
                          val isCallByNameParameter: Boolean,
                          val bodyText: Option[String],
                          val deprecatedName: Option[String],
                          val implicitType: Option[String])
  extends ScNamedStubBase[ScParameter](parent, elementType, name) with ScParameterStub