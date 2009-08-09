package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.search.ScSourceFilterScope
import api.statements.ScFunction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{StringStubIndexExtension, StubIndexKey}
import java.util.Collection

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.10.2008
 */

class ScFunctionNameIndex extends StringStubIndexExtension[ScFunction] {
  override def get(key: String, project: Project, scope: GlobalSearchScope): Collection[ScFunction] =
    super.get(key, project, new ScSourceFilterScope(scope, project))

  def getKey: StubIndexKey[String, ScFunction] = ScFunctionNameIndex.KEY
}

object ScFunctionNameIndex {
  val KEY = ScalaIndexKeys.METHOD_NAME_KEY
}