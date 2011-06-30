package org.jetbrains.plugins.scala.lang.psi.stubs.index

import org.jetbrains.plugins.scala.lang.psi.impl.search.ScSourceFilterScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.stubs.StringStubIndexExtension

/**
 * @author Alexander Podkhalyuzin
 */

class ScImplicitObjectKey extends StringStubIndexExtension[PsiClass] {

  override def get(fqn: String, project: Project, scope: GlobalSearchScope): java.util.Collection[PsiClass] =
    super.get(fqn, project, new ScSourceFilterScope(scope, project))

  def getKey = ScImplicitObjectKey.KEY
}

object ScImplicitObjectKey {
  val KEY = ScalaIndexKeys.IMPLICIT_OBJECT_KEY
}