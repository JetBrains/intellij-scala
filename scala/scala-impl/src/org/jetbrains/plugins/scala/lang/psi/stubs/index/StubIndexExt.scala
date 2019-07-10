package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{IndexSink, StubIndex, StubIndexKey}
import com.intellij.util.CommonProcessors
import org.jetbrains.plugins.scala.finder.ScalaFilterScope

import scala.collection.JavaConverters._

trait StubIndexExt[Key, Psi <: PsiElement] extends Any {

  def indexKey: StubIndexKey[Key, Psi]

  def elements(key: Key, scope: GlobalSearchScope,
               requiredClass: Class[Psi])
              (implicit project: Project): Iterable[Psi] =
    StubIndex.getElements(
      indexKey,
      key,
      project,
      ScalaFilterScope(scope),
      requiredClass
    ).asScala

  def allKeys(implicit project: Project): Iterable[Key] =
    StubIndex.getInstance.getAllKeys(indexKey, project).asScala

  def hasElements(key: Key, scope: GlobalSearchScope, requiredClass: Class[Psi])
                 (implicit project: Project): Boolean = {

    //processElements will return true only there is no elements
    val noElementsExistsProcessor = CommonProcessors.alwaysFalse[Psi]()

    !StubIndex.getInstance().processElements(indexKey, key, project, scope, requiredClass, noElementsExistsProcessor)
  }

  def occurrence(sink: IndexSink, key: Key): Unit = sink.occurrence(indexKey, key)

  def occurrences(sink: IndexSink, keys: Array[Key]): Unit = keys.foreach(occurrence(sink, _))
}
