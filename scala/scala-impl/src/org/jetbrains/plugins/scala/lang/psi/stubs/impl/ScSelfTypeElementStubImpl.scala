package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.stubs.ScSelfTypeElementStub

class ScSelfTypeElementStubImpl(parent: StubElement[_ <: PsiElement],
                                elementType: IElementType,
                                @Nullable name: String,
                                override val typeText: Option[String],
                                override val classNames: Array[String])
  extends ScNamedStubBase[ScSelfTypeElement](parent, elementType, name) with ScSelfTypeElementStub
