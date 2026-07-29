package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.stubs.ScBindingPatternStub

class ScBindingPatternStubImpl[P <: ScBindingPattern](parent: StubElement[_ <: PsiElement],
                                                      elementType: IElementType,
                                                      @Nullable name: String)
  extends ScNamedStubBase[P](parent, elementType, name) with ScBindingPatternStub[P]
