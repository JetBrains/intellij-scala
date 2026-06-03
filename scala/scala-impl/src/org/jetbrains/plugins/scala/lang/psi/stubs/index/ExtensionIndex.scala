package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndexKey
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScFunction}

final class ExtensionIndex extends ScStringStubIndexExtension[ScExtension] {
  override def getKey: StubIndexKey[String, ScExtension] = ExtensionIndex.indexKey
}

object ExtensionIndex extends ImplicitIndex[ScExtension] {
  override protected val indexKey: StubIndexKey[String, ScExtension] = ScalaIndexKeys.EXTENSION_KEY

  def extensionMethodCandidatesForFqn(classFqn: String, scope: GlobalSearchScope)
                                     (implicit project: Project): Iterable[ScFunction] =
    forClassFqn(classFqn, scope)
      .flatMap(_.extensionMethods)
}
