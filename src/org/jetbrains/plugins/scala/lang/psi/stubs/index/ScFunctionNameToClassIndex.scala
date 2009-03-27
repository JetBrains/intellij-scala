package org.jetbrains.plugins.scala.lang.psi.stubs.index

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.search.ScSourceFilterScope
import api.statements.ScFunction
import api.toplevel.typedef.ScTypeDefinition
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{StringStubIndexExtension, StubIndexKey}
import java.util.Collection

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.10.2008
 */

class ScFunctionNameToClassIndex extends StringStubIndexExtension[ScTypeDefinition] {
  override def get(key: String, project: Project, scope: GlobalSearchScope): Collection[ScTypeDefinition] =
    super.get(key, project, new ScSourceFilterScope(scope, project))

  def getKey: StubIndexKey[String, ScTypeDefinition] = ScFunctionNameToClassIndex.KEY
}

object ScFunctionNameToClassIndex {
  val KEY = ScalaIndexKeys.METHOD_NAME_TO_CLASS_KEY
}