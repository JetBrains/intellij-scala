package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension

class ScExtensionStubImpl(
  parent:                            StubElement[_ <: PsiElement],
  elementType:                       IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
  override val isTopLevel:           Boolean,
  override val topLevelQualifier:    Option[String],
  override val extensionTargetClass: Option[String]
) extends StubBase[ScExtension](parent, elementType)
    with ScExtensionStub
