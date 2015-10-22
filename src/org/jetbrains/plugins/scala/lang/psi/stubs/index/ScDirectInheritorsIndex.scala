package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import org.jetbrains.plugins.scala.finder.ScalaSourceFilterScope
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock

/**
 * User: Alexander Podkhalyuzin
 * Date: 24.10.2008
 */

class ScDirectInheritorsIndex extends StringStubIndexExtension[ScExtendsBlock] {
  override def get(int: String, project: Project, scope: GlobalSearchScope): java.util.Collection[ScExtendsBlock] =
    super.get(int, project, new ScalaSourceFilterScope(scope, project))

  def getKey = ScDirectInheritorsIndex.KEY
}

object ScDirectInheritorsIndex {
  val KEY = ScalaIndexKeys.SUPER_CLASS_NAME_KEY
}

class ScSelfTypeInheritorsIndex extends StringStubIndexExtension[ScSelfTypeElement] {
  override def get(int: String, project: Project, scope: GlobalSearchScope): java.util.Collection[ScSelfTypeElement] =
    super.get(int, project, new ScalaSourceFilterScope(scope, project))

  def getKey = ScSelfTypeInheritorsIndex.KEY
}

object ScSelfTypeInheritorsIndex {
  val KEY = ScalaIndexKeys.SELF_TYPE_CLASS_NAME_KEY
}