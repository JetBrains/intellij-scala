package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import org.jetbrains.plugins.scala.finder.ScalaSourceFilterScope

/**
 * User: Alefas
 * Date: 10.02.12
 */

class ScJavaClassNameInPackageIndex extends StringStubIndexExtension[PsiClass] {
  override def get(fqn: String, project: Project, scope: GlobalSearchScope): java.util.Collection[PsiClass] =
    super.get(fqn, project, new ScalaSourceFilterScope(scope, project))

  def getKey = ScJavaClassNameInPackageIndex.KEY
}

object ScJavaClassNameInPackageIndex {
  val KEY = ScalaIndexKeys.JAVA_CLASS_NAME_IN_PACKAGE_KEY
}
