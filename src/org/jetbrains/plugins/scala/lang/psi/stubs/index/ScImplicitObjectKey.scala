package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import org.jetbrains.plugins.scala.finder.ScalaSourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

/**
 * @author Alexander Podkhalyuzin
 */

class ScImplicitObjectKey extends StringStubIndexExtension[ScObject] {

  override def get(fqn: String, project: Project, scope: GlobalSearchScope): java.util.Collection[ScObject] =
    super.get(fqn, project, new ScalaSourceFilterScope(scope, project))

  def getKey = ScImplicitObjectKey.KEY
}

object ScImplicitObjectKey {
  val KEY = ScalaIndexKeys.IMPLICIT_OBJECT_KEY
}