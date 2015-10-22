package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import java.util.Collection

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{StringStubIndexExtension, StubIndexKey}
import org.jetbrains.plugins.scala.finder.ScalaSourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/**
 * User: Alexander Podkhalyuzin
 * Date: 14.10.2008
 */

class ScFunctionNameIndex extends StringStubIndexExtension[ScFunction] {
  override def get(key: String, project: Project, scope: GlobalSearchScope): Collection[ScFunction] =
    super.get(key, project, new ScalaSourceFilterScope(scope, project))

  def getKey: StubIndexKey[String, ScFunction] = ScFunctionNameIndex.KEY
}

object ScFunctionNameIndex {
  val KEY = ScalaIndexKeys.METHOD_NAME_KEY
}