package org.jetbrains.plugins.scala.compiler.highlighting.core

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.plugins.scala.compiler.highlighting.core.ExternalHighlighting.RangeInfo
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.ModuleKey

/** What every way of telling the user about a blocked target shares: how blockers are named, and going to them. */
private[highlighting] object BlockedTargetsPresentation:

  /**
   * The modules of the blocking targets, by their project-unique name: each module once although both of its scopes
   * may be among them.
   */
  @NlsSafe
  def blockerNames(blockers: Set[ModuleKey]): String =
    blockers.map(_.module.getName).toSeq.sorted.mkString(", ")

  /** How many modules [[blockerNames]] names. */
  def blockerCount(blockers: Set[ModuleKey]): Int = blockers.map(_.module).size

  /**
   * Opens the first error recorded in a blocking target.
   */
  def showErrors(project: Project, blockers: Set[ModuleKey]): Unit =
    val recorded = CompilerGeneratedStateManager.get(project).toHighlightingState
    recorded.filesWithHighlightings(ErrorTypes).toSeq.sortBy(_.getPath)
      .find(file => file.isValid && ModuleKey.of(project, file).exists(blockers.contains)) // just take the first
      .foreach: file =>
        val PosInfo(line, column) = recorded.externalHighlightings(file)
          .collect { case ExternalHighlighting(tpe, _, Some(range), _) if ErrorTypes.contains(tpe) => startOf(range) }
          .minByOption(pos => pos.line -> pos.column)
          .getOrElse(PosInfo(1, 1))
        new OpenFileDescriptor(project, file, (line - 1) max 0, (column - 1) max 0).navigate(true)

  private val ErrorTypes: Set[HighlightInfoType] = Set(HighlightInfoType.ERROR, HighlightInfoType.WRONG_REF)

  private def startOf(range: RangeInfo): PosInfo = range match
    case RangeInfo.Range(start, _, _) => start
    case RangeInfo.Pointer(pointer) => pointer
