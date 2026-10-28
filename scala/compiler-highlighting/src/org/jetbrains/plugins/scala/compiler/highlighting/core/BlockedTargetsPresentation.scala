package org.jetbrains.plugins.scala.compiler.highlighting.core

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.plugins.scala.compiler.highlighting.core.ExternalHighlighting.RangeInfo
import org.jetbrains.plugins.scala.compiler.highlighting.services.core.ModuleKey
import org.jetbrains.plugins.scala.compiler.highlighting.util.CompilerHighlightingBundle
import org.jetbrains.sbt.SbtSourceSetUtil.SbtSourceSetModuleExt

/** What every way of telling the user about a blocked target shares: how blockers are named, and going to them. */
private[highlighting] object BlockedTargetsPresentation:

  /**
   * The blocking targets as HTML, each by its sbt project's own name, e.g. `C`, or by its module name, e.g.
   * `root.C`, where another blocker's sbt project has the same name: projects of different builds may share one.
   * Both scopes of a project are one name. Only the first few are named when there are many, see [[capped]].
   */
  @Nls
  def blockerNames(blockers: Set[ModuleKey]): String =
    val shortNameByFullName = blockers.iterator
      .map(key => key.module.nameWithoutSourceSet -> key.module.displayNameWithoutSourceSet)
      .toMap
    val (named, more) = capped(shownNames(shortNameByFullName))
    val listed = named.map(StringUtil.escapeXmlEntities).mkString(", ")
    if more == 0 then listed
    else CompilerHighlightingBundle.message("highlighting.blocked.names.and.more", listed, more)

  /**
   * The names to list out of `names`, and how many are left out: all of them when there are at most [[MaxListed]],
   * else one fewer, so that a name is never left out alone, where naming it takes as much room.
   */
  private[core] def capped(names: Seq[String]): (Seq[String], Int) =
    if names.size <= MaxListed then (names, 0)
    else (names.take(MaxListed - 1), names.size - MaxListed + 1)

  private val MaxListed = 3

  /**
   * The names [[blockerNames]] shows, sorted, for blockers given by their full name with their sbt project's own
   * name, if they have one: each by its own name, or by its full name when it has none or another blocker would be
   * shown by the same one. Full names are unique, so no two blockers are shown alike.
   */
  private[core] def shownNames(shortNameByFullName: Map[String, Option[String]]): Seq[String] =
    val preferred = shortNameByFullName.map((fullName, shortName) => fullName -> shortName.getOrElse(fullName))
    val uses = preferred.values.groupBy(identity).view.mapValues(_.size)
    preferred
      .map((fullName, name) => if uses(name) == 1 then name else fullName)
      .toSeq.sorted

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
