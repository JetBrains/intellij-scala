package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{CharSequenceHashStubIndexExtension, StubIndex, StubIndexKey}
import com.intellij.util.CommonProcessors.alwaysFalse
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import java.util
import scala.reflect.{ClassTag, classTag}

abstract class ScCharSequenceHashStubIndexExtension[Psi <: PsiElement : ClassTag] extends CharSequenceHashStubIndexExtension[Psi] {

  @deprecated("Deprecated base method, please use ScCharSequenceHashStubIndexExtension#getElements", "2023.3")
  @deprecatedOverriding
  override final def get(key: CharSequence, project: Project, scope: GlobalSearchScope): util.Collection[Psi] = {
    getElements(key, project, scope)
  }

  protected def preprocessKey(key: CharSequence): CharSequence

  final def getElements(key: CharSequence, project: Project, scope: GlobalSearchScope): util.Collection[Psi] = {
    val keyPreprocessed = preprocessKey(key)
    val requiredClass = classTag[Psi].runtimeClass.asInstanceOf[Class[Psi]]
    val scalaScope = ScalaFilterScope(scope)(project)
    StubIndex.getElements(getKey, keyPreprocessed, project, scalaScope, requiredClass)
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

  override def getVersion: Int = 2 // Workaround for SCL-21752
}

abstract class ScFqnHashStubIndexExtension[Psi <: PsiElement : ClassTag] extends ScCharSequenceHashStubIndexExtension[Psi] {
  override protected def preprocessKey(fqn: CharSequence): CharSequence =
    ScalaNamesUtil.cleanFqn(fqn.toString)
}