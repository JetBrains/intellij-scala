package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{StubBase, StubElement}
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExtendsBlockStub

import scala.collection.immutable.ArraySeq

class ScExtendsBlockStubImpl(parent: StubElement[_ <: PsiElement],
                             elementType: IElementType,
                             override val baseClasses: ArraySeq[String])
  extends StubBase[ScExtendsBlock](parent, elementType) with ScExtendsBlockStub
