package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.search.ScSourceFilterScope
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{StringStubIndexExtension, StubIndexKey}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScVariable

/**
 * User: Alexander Podkhalyuzin
 * Date: 18.10.2008
 */

class ScVariableNameIndex extends StringStubIndexExtension[ScVariable] {
  override def get(key: String, project: Project, scope: GlobalSearchScope): java.util.Collection[ScVariable] =
    super.get(key, project, new ScSourceFilterScope(scope, project))

  def getKey: StubIndexKey[String, ScVariable] = ScVariableNameIndex.KEY
}

object ScVariableNameIndex {
  val KEY = ScalaIndexKeys.VARIABLE_NAME_KEY
}