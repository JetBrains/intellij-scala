package org.jetbrains.plugins.scala.lang.psi
package stubs
package index

import java.util

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IntStubIndexExtension
import org.jetbrains.plugins.scala.finder.ScalaFilterScope

/**
  * @author adkozlov
  */
abstract class ScIntStubIndexExtension[E <: PsiElement] extends IntStubIndexExtension[E] {

  override final def get(key: Integer, project: Project, scope: GlobalSearchScope): util.Collection[E] =
    super.get(key, project, new ScalaFilterScope(scope, project))
}
