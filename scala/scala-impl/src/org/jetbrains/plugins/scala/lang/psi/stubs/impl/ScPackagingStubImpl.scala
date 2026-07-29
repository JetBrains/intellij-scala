package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.stubs.StubBase
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.stubs.{RawStubElement, ScPackagingStub}

final class ScPackagingStubImpl(parent: RawStubElement,
                                elementType: IElementType,
                                override val packageName: String,
                                override val parentPackageName: String,
                                override val isExplicit: Boolean)
  extends StubBase[ScPackaging](parent, elementType) with ScPackagingStub
