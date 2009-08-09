package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import _root_.org.jetbrains.plugins.scala.lang.psi.impl.search.ScSourceFilterScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import api.toplevel.templates.ScExtendsBlock

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.10.2008
 */

class ScDirectInheritorsIndex extends StringStubIndexExtension[ScExtendsBlock] {
  override def get(int: String, project: Project, scope: GlobalSearchScope): java.util.Collection[ScExtendsBlock] =
    super.get(int, project, new ScSourceFilterScope(scope, project))

  def getKey = ScDirectInheritorsIndex.KEY
}

object ScDirectInheritorsIndex {
  val KEY = ScalaIndexKeys.SUPER_CLASS_NAME_KEY
}