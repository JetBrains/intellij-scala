package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package index

import java.{util => ju}

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.{IndexSink, StubIndex, StubIndexKey}
import com.intellij.util.CommonProcessors.CollectUniquesProcessor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

trait ImplicitIndex {

  protected val indexKey: StubIndexKey[String, ScMember]

  protected def occurrence(sink: IndexSink, name: String): Unit =
    sink.occurrence(indexKey, name)

  def occurrences(sink: IndexSink, names: Array[String]): Unit =
    names.foreach(occurrence(sink, _))

  def forClassFqn(qualifiedName: String, scope: GlobalSearchScope)
                 (implicit project: Project): collection.Set[ScMember] = {
    val stubIndex = StubIndex.getInstance
    val collectProcessor = new CollectMembersProcessor

    for {
      segments <- ScalaNamesUtil.splitName(qualifiedName).tails
      if segments.nonEmpty

      name = segments.mkString(".")
    } stubIndex.processElements(
      indexKey,
      name,
      project,
      scope,
      classOf[ScMember],
      collectProcessor
    )

    collectProcessor.results
  }

  //noinspection TypeAnnotation
  private final class CollectMembersProcessor extends CollectUniquesProcessor[ScMember] {

    def results = {
      import collection.JavaConverters._
      getResults.asScala
    }

    override def getResults =
      super.getResults.asInstanceOf[ju.Set[ScMember]]
  }
}