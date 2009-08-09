package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index
import _root_.org.jetbrains.plugins.scala.lang.psi.impl.search.ScSourceFilterScope
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{StubIndexKey, IntStubIndexExtension}
import com.intellij.psi.PsiClass
import api.toplevel.typedef.ScTypeDefinition

/**
 * @author ilyas
 */

class ScFullClassNameIndex extends IntStubIndexExtension[PsiClass] {

  override def get(int: java.lang.Integer, project: Project, scope: GlobalSearchScope): java.util.Collection[PsiClass] =
    super.get(int, project, new ScSourceFilterScope(scope, project))

  def getKey = ScFullClassNameIndex.KEY
}

object ScFullClassNameIndex {
  val KEY = ScalaIndexKeys.FQN_KEY;
}