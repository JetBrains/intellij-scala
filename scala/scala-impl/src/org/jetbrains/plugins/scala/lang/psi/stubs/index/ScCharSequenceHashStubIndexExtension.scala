package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{CharSequenceHashStubIndexExtension, StubIndex, StubIndexKey}
import com.intellij.util.CommonProcessors.alwaysFalse
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import java.util
import scala.jdk.CollectionConverters.IterableHasAsScala

abstract class ScCharSequenceHashStubIndexExtension[Psi <: PsiElement] extends CharSequenceHashStubIndexExtension[Psi] {

  override final def get(key: CharSequence, project: Project, scope: GlobalSearchScope): util.Collection[Psi] = {
    val keyPreprocessed = preprocessKey(key)
    super.get(keyPreprocessed, project, ScalaFilterScope(scope)(project))
  }

  protected def preprocessKey(key: CharSequence): CharSequence

  final def elementsByHash(key: CharSequence, project: Project, scope: GlobalSearchScope): Iterable[Psi] = {
    val collection = get(key, project, scope)
    collection.asScala
  }

  final def hasElement(key: CharSequence, project: Project, scope: GlobalSearchScope, requiredClass: Class[Psi]): Boolean = {
    val indexKey: StubIndexKey[CharSequence, Psi] = getKey
    val keyPreprocessed: CharSequence = preprocessKey(key)

    // processElements will return true only there is no elements
    val noElements = StubIndex.getInstance.processElements(
      indexKey,
      keyPreprocessed,
      project,
      scope,
      requiredClass,
      alwaysFalse[Psi]
    )
    !noElements
  }
}

abstract class ScFqnHashStubIndexExtension[Psi <: PsiElement] extends ScCharSequenceHashStubIndexExtension[Psi] {
  override protected def preprocessKey(fqn: CharSequence): CharSequence =
    ScalaNamesUtil.cleanFqn(fqn.toString)
}