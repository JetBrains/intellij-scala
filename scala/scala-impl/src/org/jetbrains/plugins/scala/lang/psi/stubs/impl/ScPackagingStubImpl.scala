package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.stubs.StubBase

final class ScPackagingStubImpl(parent: RawStubElement,
                                elementType: RawStubElementType,
                                override val packageName: String,
                                override val parentPackageName: String,
                                override val isExplicit: Boolean)
  extends StubBase[api.toplevel.ScPackaging](parent, elementType) with ScPackagingStub