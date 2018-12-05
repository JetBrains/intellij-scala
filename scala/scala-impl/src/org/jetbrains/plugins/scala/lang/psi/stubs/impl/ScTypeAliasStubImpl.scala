package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

/**
  * User: Alexander Podkhalyuzin
  * Date: 18.10.2008
  */
class ScTypeAliasStubImpl(parent: StubElement[_ <: PsiElement],
                          elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                          name: String,
                          val typeText: Option[String],
                          val lowerBoundText: Option[String],
                          val upperBoundText: Option[String],
                          val isLocal: Boolean,
                          val isDeclaration: Boolean,
                          val isStableQualifier: Boolean)
  extends ScNamedStubBase[ScTypeAlias](parent, elementType, name) with ScTypeAliasStub
