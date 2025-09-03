package org.jetbrains.plugins.scala.lang.psi.stubs

import com.intellij.psi.stubs.NamedStub
import org.jetbrains.plugins.scala.lang.ir.typeTree.TypeTreeHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScContextBound
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTypeElementOwnerStub

trait ScContextBoundStub extends ScTypeElementOwnerStub[ScContextBound] with NamedStub[ScContextBound] {
  override def typeTreeHolder: Some[TypeTreeHolder]
}
