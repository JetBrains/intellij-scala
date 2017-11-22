package org.jetbrains.plugins.scala.lang.psi
package stubs
package index

import java.util

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import org.jetbrains.plugins.scala.finder.ScalaFilterScope

/**
  * @author adkozlov
  */
abstract class ScStringStubIndexExtension[E <: PsiElement] extends StringStubIndexExtension[E] {

  override final def get(key: String, project: Project, scope: GlobalSearchScope): util.Collection[E] =
    super.get(key, project, new ScalaFilterScope(scope, project))
}
