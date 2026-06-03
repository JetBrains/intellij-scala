package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{IndexSink, StubIndexKey}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys.StubIndexStringKeyExt

import scala.reflect.ClassTag

abstract class ImplicitIndex[Psi <: ScMember : ClassTag] {
  protected val indexKey: StubIndexKey[String, Psi]

  def occurrence(sink: IndexSink, name: String): Unit =
    sink.occurrence(indexKey, name)

  def forClassFqn(qualifiedName: String, scope: GlobalSearchScope)(implicit project: Project): Set[Psi] =
    indexKey.forClassFqn(qualifiedName, scope)
}
