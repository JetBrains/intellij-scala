package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.incremental.scala.Client.PosInfo

sealed trait HighlightingState {
  def externalHighlightings(file: VirtualFile): Set[ExternalHighlighting]

  def externalTypes(file: VirtualFile): Map[(PosInfo, PosInfo), String]

  def filesWithHighlightings: Set[VirtualFile]

  def filesWithHighlightings(types: Set[HighlightInfoType]): Set[VirtualFile]
}

object HighlightingState {
  def apply(filesToState: Map[VirtualFile, FileCompilerGeneratedState]): HighlightingState = {
    //without this filtering files might contain some old deleted files
    val validFiles = filesToState.filter(_._1.isValid)
    HighlightingStateImpl(validFiles)
  }

  private case class HighlightingStateImpl(private val filesToState: Map[VirtualFile, FileCompilerGeneratedState]) extends HighlightingState {

    override def externalHighlightings(file: VirtualFile): Set[ExternalHighlighting] =
      filesToState.get(file).map(_.highlightings).getOrElse(Set.empty)

    override def externalTypes(file: VirtualFile): Map[(PosInfo, PosInfo), String] =
      filesToState.get(file).map(_.types).getOrElse(Map.empty)

    override def filesWithHighlightings: Set[VirtualFile] = filesToState.filter {
      case (_, state) => state.highlightings.nonEmpty
    }.keySet

    override def filesWithHighlightings(types: Set[HighlightInfoType]): Set[VirtualFile] = filesToState.filter {
      case (_, state) => state.highlightings.map(_.highlightType).exists(types.contains)
    }.keySet
  }

}