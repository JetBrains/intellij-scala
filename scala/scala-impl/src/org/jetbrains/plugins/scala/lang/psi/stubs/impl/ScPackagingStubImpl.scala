package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package impl

import com.intellij.psi.stubs.StubBase

final class ScPackagingStubImpl(parent: RawStubElement,
                                elementType: RawStubElementType,
                                val packageName: String,
                                val parentPackageName: String,
                                val isExplicit: Boolean)
  extends StubBase[api.toplevel.ScPackaging](parent, elementType) with ScPackagingStub