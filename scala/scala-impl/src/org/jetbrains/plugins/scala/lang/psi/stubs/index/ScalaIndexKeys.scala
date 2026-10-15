package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{StubIndex, StubIndexKey}
import org.jetbrains.plugins.scala.extensions.CollectUniquesProcessorEx
import org.jetbrains.plugins.scala.finder.ScalaFilterScope
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import scala.reflect.ClassTag

/** The [[ScalaStubIndexKeys]] and lookups in their indexes */
object ScalaIndexKeys {
  export ScalaStubIndexKeys.*

  implicit class StubIndexKeyExt[Key, Psi <: PsiElement: ClassTag](private val indexKey: StubIndexKey[Key, Psi]) {

    import scala.jdk.CollectionConverters._

    def elements(key: Key, scope: GlobalSearchScope)
                (implicit project: Project): Iterable[Psi] =
      StubIndex.getElements(
        indexKey,
        key,
        project,
        ScalaFilterScope(scope),
        implicitly[ClassTag[Psi]].runtimeClass.asInstanceOf[Class[Psi]]
      ).asScala

    def allKeys(implicit project: Project): Iterable[Key] =
      StubIndex.getInstance.getAllKeys(indexKey, project).asScala
  }

  implicit class StubIndexStringKeyExt[Psi <: PsiElement : ClassTag](private val indexKey: StubIndexKey[String, Psi]) {

    def forClassFqn(qualifiedName: String, scope: GlobalSearchScope)
                   (implicit project: Project): Set[Psi] = {
      val stubIndex = StubIndex.getInstance
      val collectProcessor = new CollectUniquesProcessorEx[Psi]

      for {
        segments <- ScalaNamesUtil.splitName(qualifiedName).tails
        if segments.nonEmpty

        name = segments.mkString(".")
      } stubIndex.processElements(
        indexKey,
        name,
        project,
        scope,
        implicitly[ClassTag[Psi]].runtimeClass.asInstanceOf[Class[Psi]],
        collectProcessor
      )

      collectProcessor.results
    }

  }

}
