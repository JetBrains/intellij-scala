package org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging

import com.intellij.psi.stubs.IStubElementType
import stubs.elements.wrappers.StubBasedPsiElementWrapper
import stubs.ScPackageContainerStub
import typedef.ScTypeDefinition

/**
 * @author ilyas
 */

trait ScPackageContainer extends ScalaPsiElement
with StubBasedPsiElementWrapper[ScPackageContainerStub, ScPackageContainer]{

  def fqn: String

  def packagings: Seq[ScPackaging]

  def typeDefs: Seq[ScTypeDefinition]

}