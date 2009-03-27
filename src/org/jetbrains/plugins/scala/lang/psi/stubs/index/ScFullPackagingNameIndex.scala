package org.jetbrains.plugins.scala.lang.psi.stubs.index

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.search.ScSourceFilterScope
import api.toplevel.packaging.ScPackageContainer
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IntStubIndexExtension

/**
 * @author ilyas
 */

class ScFullPackagingNameIndex extends IntStubIndexExtension[ScPackageContainer] {

  override def get(int: java.lang.Integer, project: Project, scope: GlobalSearchScope): java.util.Collection[ScPackageContainer] =
    super.get(int, project, new ScSourceFilterScope(scope, project));

  def getKey = ScFullPackagingNameIndex.KEY
}

object ScFullPackagingNameIndex {
  val KEY = ScalaIndexKeys.PACKAGE_FQN_KEY
}