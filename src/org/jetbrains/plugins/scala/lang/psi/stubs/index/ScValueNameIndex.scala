package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.search.ScSourceFilterScope
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{StringStubIndexExtension, StubIndexKey}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValue
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.10.2008
 */

class ScValueNameIndex extends StringStubIndexExtension[ScValue] {
  override def get(key: String, project: Project, scope: GlobalSearchScope): java.util.Collection[ScValue] =
    super.get(key, project, new ScSourceFilterScope(scope, project))

  def getKey: StubIndexKey[String, ScValue] = ScValueNameIndex.KEY
}

object ScValueNameIndex {
  val KEY = ScalaIndexKeys.VALUE_NAME_KEY
}

class ScClassParameterNameIndex extends StringStubIndexExtension[ScClassParameter] {
  override def get(key: String, project: Project, scope: GlobalSearchScope): java.util.Collection[ScClassParameter] =
    super.get(key, project, new ScSourceFilterScope(scope, project))

  def getKey: StubIndexKey[String, ScClassParameter] = ScClassParameterNameIndex.KEY
}

object ScClassParameterNameIndex {
  val KEY = ScalaIndexKeys.CLASS_PARAMETER_NAME_KEY
}