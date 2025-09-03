package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import org.jetbrains.plugins.scala.lang.ir.typeTree.TypeTreeHolder
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeAliasStub

import scala.collection.immutable.ArraySeq

class ScTypeAliasStubImpl(
  parent:                          StubElement[_ <: PsiElement],
  elementType:                     IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
  name:                            String,
  override val typeTreeHolder:     Option[TypeTreeHolder],
  override val lowerBoundTypeTree: Option[TypeTreeHolder],
  override val upperBoundTypeTree: Option[TypeTreeHolder],
  override val isLocal:            Boolean,
  override val isDeclaration:      Boolean,
  override val isStableQualifier:  Boolean,
  override val stableQualifier:    Option[String],
  override val isTopLevel:         Boolean,
  override val topLevelQualifier:  Option[String],
  override val classType:          Option[String]
) extends ScNamedStubBase[ScTypeAlias](parent, elementType, name)
    with ScTypeAliasStub {
  override def viewBoundsTypeTrees: ArraySeq[TypeTreeHolder] = ArraySeq.empty
}
