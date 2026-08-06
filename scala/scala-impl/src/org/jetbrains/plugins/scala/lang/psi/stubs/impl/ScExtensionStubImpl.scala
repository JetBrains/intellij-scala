package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExtensionStub

class ScExtensionStubImpl(
  parent:                            StubElement[_ <: PsiElement],
  elementType:                       IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
  override val isTopLevel:           Boolean,
  override val topLevelQualifier:    Option[String],
  override val extensionTargetClass: Option[String]
) extends StubBase[ScExtension](parent, elementType)
    with ScExtensionStub
