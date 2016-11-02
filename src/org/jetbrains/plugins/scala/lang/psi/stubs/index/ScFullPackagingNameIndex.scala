package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IntStubIndexExtension
import org.jetbrains.plugins.scala.finder.ScalaSourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging

/**
 * @author ilyas
 */

class ScFullPackagingNameIndex extends IntStubIndexExtension[ScPackaging] {

  override def get(int: java.lang.Integer, project: Project, scope: GlobalSearchScope): java.util.Collection[ScPackaging] =
    super.get(int, project, new ScalaSourceFilterScope(scope, project))

  def getKey = ScFullPackagingNameIndex.KEY
}

object ScFullPackagingNameIndex {
  val KEY = ScalaIndexKeys.PACKAGE_FQN_KEY
}