package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import com.intellij.util.io.StringRef
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

/**
  * User: Alexander Podkhalyuzin
  * Date: 18.10.2008
  */
class ScTypeAliasStubImpl(parent: StubElement[_ <: PsiElement],
                          elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                          nameRef: StringRef,
                          protected[impl] val typeTextRef: Option[StringRef],
                          protected[impl] val lowerBoundTextRef: Option[StringRef],
                          protected[impl] val upperBoundTextRef: Option[StringRef],
                          val isLocal: Boolean,
                          val isDeclaration: Boolean,
                          val isStableQualifier: Boolean)
  extends ScNamedStubBase[ScTypeAlias](parent, elementType, nameRef) with ScTypeAliasStub
    with ScTypeElementOwnerStub[ScTypeAlias] with ScBoundsOwnerStub[ScTypeAlias]
