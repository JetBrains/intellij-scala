package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import java.util

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.search.ScSourceFilterScope
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{StringStubIndexExtension, StubIndexKey}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.10.2008
 */

class ScTypeAliasNameIndex extends StringStubIndexExtension[ScTypeAlias] {
  override def get(key: String, project: Project, scope: GlobalSearchScope): util.Collection[ScTypeAlias] =
    super.get(key, project, new ScSourceFilterScope(scope, project))

  def getKey: StubIndexKey[String, ScTypeAlias] = ScTypeAliasNameIndex.KEY
}

object ScTypeAliasNameIndex {
  val KEY = ScalaIndexKeys.TYPE_ALIAS_NAME_KEY
}

class ScStableTypeAliasNameIndex extends StringStubIndexExtension[ScTypeAlias] {
  override def get(key: String, project: Project, scope: GlobalSearchScope): util.Collection[ScTypeAlias] =
    super.get(key, project, new ScSourceFilterScope(scope, project))

  def getKey: StubIndexKey[String, ScTypeAlias] = ScStableTypeAliasNameIndex.KEY
}

object ScStableTypeAliasNameIndex {
  val KEY = ScalaIndexKeys.STABLE_ALIAS_NAME_KEY
}