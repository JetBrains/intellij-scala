package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

class ScParameterStubImpl(parent: StubElement[_ <: PsiElement],
                          elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                          name: String,
                          override val typeText: Option[String],
                          override val isStable: Boolean,
                          override val isDefaultParameter: Boolean,
                          override val isRepeated: Boolean,
                          override val isVal: Boolean,
                          override val isVar: Boolean,
                          override val isCallByNameParameter: Boolean,
                          override val bodyText: Option[String],
                          override val deprecatedName: Option[String],
                          override val implicitClassNames: Array[String])
  extends ScNamedStubBase[ScParameter](parent, elementType, name) with ScParameterStub