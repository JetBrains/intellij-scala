package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubElement}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.ir.typeTree.TypeTreeHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScContextBound
import org.jetbrains.plugins.scala.lang.psi.stubs.ScContextBoundStub

final class ScContextBoundStubImpl(parent: StubElement[_ <: PsiElement],
                                   elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                                   @Nullable
                                   name: String,
                                   override val typeTreeHolder: Some[TypeTreeHolder])
  extends ScNamedStubBase[ScContextBound](parent, elementType, name)
    with ScContextBoundStub