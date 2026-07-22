package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.*
import org.jetbrains.plugins.scala.compiler.highlighting.compilation.SourceRole

/** What one VFS event means for one project's build. */
private enum SourceChange {

  /** A compiled source appeared; its file is resolvable only once the change has been applied. */
  case Created(event: VFileCreateEvent)

  /** A compiled source's content no longer matches the output built from it. */
  case Modified(file: VirtualFile)

  /** A compiled source was deleted. */
  case Deleted(file: VirtualFile)

  /** A compiled source was moved or renamed, so nothing is built from where it used to be. */
  case Relocated(file: VirtualFile)

  /** Another build input changed: no output is current, but no file has to enter a compilation scope. */
  case Outdated

  /** A source directory appeared or disappeared, and what it held cannot be enumerated. */
  case Restructured
}

private object SourceChange {

  /**
   * Classifies one platform event for one project, or discards it.
   *
   * An edit made through the editor arrives twice: once as a PSI event, and again here when the document is
   * written to disk before a compilation. That second one is the plugin's own write, and recording it would
   * advance the epoch underneath a compilation that has already snapshotted it, leaving the target it built
   * permanently behind. Saves are therefore dropped, and the PSI listener stays the single source for files
   * changed through an editor.
   *
   */
  def of(fileIndex: ProjectFileIndex, event: VFileEvent): Option[SourceChange] = event match {
    case e: VFileCreateEvent if e.isDirectory => restructuring(fileIndex, e.getParent)
    case e: VFileCreateEvent => byRole(SourceRole.ofNewFile(fileIndex, e.getParent, e.getChildName), Created(e))
    case e: VFileContentChangeEvent if e.isFromSave => None
    case e: VFileContentChangeEvent => changeTo(fileIndex, e.getFile, Modified.apply)
    case e: VFileDeleteEvent => changeTo(fileIndex, e.getFile, Deleted.apply)
    case e: VFileMoveEvent => changeTo(fileIndex, e.getFile, Relocated.apply)
    case e: VFilePropertyChangeEvent if e.isRename => changeTo(fileIndex, e.getFile, Relocated.apply)
    case _ => None
  }

  private def changeTo(fileIndex: ProjectFileIndex,
                       file: VirtualFile,
                       whenCompiled: VirtualFile => SourceChange): Option[SourceChange] =
    if (file.isDirectory) restructuring(fileIndex, file)
    else byRole(SourceRole.of(fileIndex, file), whenCompiled(file))

  private def restructuring(fileIndex: ProjectFileIndex, directory: VirtualFile): Option[SourceChange] =
    Option.when(fileIndex.isInSourceContent(directory))(Restructured)

  private def byRole(role: SourceRole, whenCompiled: => SourceChange): Option[SourceChange] = role match {
    case SourceRole.Compiled => Some(whenCompiled)
    case SourceRole.BuildInput => Some(Outdated)
    case SourceRole.Untracked => None
  }
}
