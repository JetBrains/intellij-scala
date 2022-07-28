package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable

final class ScPropertyStubImpl[P <: ScValueOrVariable](
  parent:                         StubElement[_ <: PsiElement],
  elementType:                    IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
  override val isDeclaration:     Boolean,
  override val isImplicit:        Boolean,
  override val names:             Array[String],
  override val typeText:          Option[String],
  override val bodyText:          Option[String],
  override val isLocal:           Boolean,
  override val classNames:        Array[String],
  override val isTopLevel:        Boolean,
  override val topLevelQualifier: Option[String]
) extends StubBase[P](parent, elementType) with ScPropertyStub[P]
