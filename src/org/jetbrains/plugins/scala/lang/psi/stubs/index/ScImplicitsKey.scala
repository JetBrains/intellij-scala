package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.impl.search.ScSourceFilterScope

/**
 * @author Alexander Podkhalyuzin
 */

class ScImplicitsKey extends StringStubIndexExtension[ScMember] {

  override def get(fqn: String, project: Project, scope: GlobalSearchScope): java.util.Collection[ScMember] =
    super.get(fqn, project, new ScSourceFilterScope(scope, project))

  def getKey = ScImplicitsKey.KEY
}

object ScImplicitsKey {
  val KEY = ScalaIndexKeys.IMPLICITS_KEY
}