package org.jetbrains.plugins.scala.lang.psi.stubs.index


import com.intellij.openapi.project.Project
import com.intellij.psi.stubs.IntStubIndexExtension
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import psi.impl.search.ScSourceFilterScope

/**
 * @author ilyas
 */

class ScPackageObjectIndex extends IntStubIndexExtension[PsiClass] {

  override def get(int: java.lang.Integer, project: Project, scope: GlobalSearchScope): java.util.Collection[PsiClass] =
    super.get(int, project, new ScSourceFilterScope(scope, project))

  def getKey = ScPackageObjectIndex.KEY
}

object ScPackageObjectIndex {
  val KEY = ScalaIndexKeys.PACKAGE_OBJECT_KEY;
}