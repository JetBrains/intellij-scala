package org.jetbrains.plugins.scala.lang.psi.stubs.impl

import com.intellij.psi.stubs.StubBase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.stubs.{RawStubElement, RawStubElementType, ScPackagingStub}

final class ScPackagingStubImpl(parent: RawStubElement,
                                elementType: RawStubElementType,
                                override val packageName: String,
                                override val parentPackageName: String,
                                override val isExplicit: Boolean)
  extends StubBase[ScPackaging](parent, elementType) with ScPackagingStub