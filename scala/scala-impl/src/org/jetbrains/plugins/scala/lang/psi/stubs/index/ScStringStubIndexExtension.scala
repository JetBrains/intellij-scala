package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{StringStubIndexExtension, StubIndex}
import org.jetbrains.plugins.scala.finder.ScalaFilterScope

import java.{util => ju}
import scala.reflect.{ClassTag, classTag}

abstract class ScStringStubIndexExtension[E <: PsiElement : ClassTag] extends StringStubIndexExtension[E] {

  @deprecated("Deprecated base method, please use ScStringStubIndexExtension#getElements", "2023.3")
  @deprecatedOverriding
  override final def get(key: String, project: Project, scope: GlobalSearchScope): ju.Collection[E] =
    getElements(key, project, scope)

  final def getElements(key: String, project: Project, scope: GlobalSearchScope): ju.Collection[E] = {
    val requiredClass = classTag[E].runtimeClass.asInstanceOf[Class[E]]
    val scalaScope = ScalaFilterScope(scope)(project)
    StubIndex.getElements(getKey, key, project, scalaScope, requiredClass)
  }
}
