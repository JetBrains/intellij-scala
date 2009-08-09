package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.search.ScSourceFilterScope
import api.statements.{ScFunction, ScTypeAlias}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{StringStubIndexExtension, StubIndexKey}
import java.util.Collection

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.10.2008
 */

class ScTypeAliasNameIndex extends StringStubIndexExtension[ScTypeAlias] {
  override def get(key: String, project: Project, scope: GlobalSearchScope): Collection[ScTypeAlias] =
    super.get(key, project, new ScSourceFilterScope(scope, project))

  def getKey: StubIndexKey[String, ScTypeAlias] = ScTypeAliasNameIndex.KEY
}

object ScTypeAliasNameIndex {
  val KEY = ScalaIndexKeys.TYPE_ALIAS_NAME_KEY
}