package org.jetbrains.jps.incremental.scala.local.zinc

import java.io.File
import java.util.Optional

import org.jetbrains.jps.incremental.scala.Client
import org.jetbrains.plugins.scala.compiler.data.CompilationData
import sbt.internal.inc._
import xsbti.compile._

import scala.jdk.CollectionConverters._

case class IntellijExternalLookup(compilationData: CompilationData, client: Client, isCached: Boolean)
  extends ExternalLookup {

  private val all = compilationData.zincData.allSources.toSet
  private val changedSources = compilationData.sources.toSet

  override def changedSources(previousAnalysis: CompileAnalysis): Option[Changes[File]] =
    if (isCached) None else {
    val previousSources = previousAnalysis.readStamps().getAllSourceStamps.keySet().asScala.toSet

    Some(new UnderlyingChanges[File] {
      override def added: Set[File] = all -- previousSources

      override def removed: Set[File] = previousSources -- all

      override def changed: Set[File] = changedSources & previousSources

      override def unmodified: Set[File] = previousSources -- changedSources
    })
  }

  override def changedBinaries(previousAnalysis: CompileAnalysis): Option[Set[File]] = Some(Set())

  override def removedProducts(previousAnalysis: CompileAnalysis): Option[Set[File]] = Some(Set())

  override def shouldDoIncrementalCompilation(changedClasses: Set[String], analysis: CompileAnalysis): Boolean = {
    if (compilationData.zincData.isCompile){
      def invalidateClass(source: File): Unit = client.sourceStarted(source.getAbsolutePath)

      changedClasses.flatMap(analysis.asInstanceOf[Analysis].relations.definesClass).foreach(invalidateClass)
    }

    !compilationData.zincData.isCompile
  }

  override def hashClasspath(classpath: Array[File]): Optional[Array[FileHash]] = Optional.empty()
}