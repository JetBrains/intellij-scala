package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IntStubIndexExtension
import org.jetbrains.plugins.scala.finder.ScalaSourceFilterScope

/**
 * @author ilyas
 */

class ScFullClassNameIndex extends IntStubIndexExtension[PsiClass] {

  override def get(int: java.lang.Integer, project: Project, scope: GlobalSearchScope): java.util.Collection[PsiClass] =
    super.get(int, project, new ScalaSourceFilterScope(scope, project))

  def getKey = ScFullClassNameIndex.KEY
}

object ScFullClassNameIndex {
  val KEY = ScalaIndexKeys.FQN_KEY
}