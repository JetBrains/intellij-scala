package org.jetbrains.plugins.scala.compiler.highlighting.compilation

import com.intellij.openapi.fileTypes.{FileTypeRegistry, LanguageFileType}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ProjectFileIndex, ProjectRootManager}
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.scala.finder.ScalaLanguageDerivative

/** What a changed file is to compiler-based highlighting. */
enum SourceRole:

  /** The build compiles it into a module output, and a compilation of ours can hold it in its scope. */
  case Compiled

  /** Our compilations read what the build produces from it, but no scope of ours can hold it. */
  case BuildInput

  /**
   * Nothing the compilers read is produced from it, so changing it cannot affect a diagnostic: resources,
   * worksheets, and everything outside a source root.
   *
   * Worksheets belong here for a second reason as well. JPS does not compile a `.sc` at all, so one could
   * never leave the `modifiedFiles` state — and while it sat there it would be added to the scope of every other
   * file's incremental compilation, putting a file JPS cannot build into a JPS request. A worksheet still
   * gets its preparatory incremental when it needs one.
   */
  case Untracked


object SourceRole:

  /**
   * What the JPS builders declare as `getCompilableFileExtensions`. These are the only files a
   * [[FileCompilationScope]] is ever built for, and
   * a compilation scope is the only thing that clears a recorded modification, so recording a file outside
   * this set would hold every later decision on the incremental path for the rest of the session.
   */
  private val CompiledExtensions = Set("scala", "java")

  /**
   * Other JVM languages, for mixed projects. The build produces class files from them 
   * that our compilations read, so a change makes the outputs stale, but no scope of ours can hold one.
   */
  private val OtherJvmSourceExtensions = Set("kt", "kts", "groovy")

  def of(project: Project, file: VirtualFile): SourceRole =
    of(ProjectRootManager.getInstance(project).getFileIndex, file)

  def of(fileIndex: ProjectFileIndex, file: VirtualFile): SourceRole =
    roleOf(fileIndex.isInSourceContent(file), file.getName)

  /** The role of a file that does not exist yet, being created as `name` inside `directory`. */
  def ofNewFile(fileIndex: ProjectFileIndex, directory: VirtualFile, name: String): SourceRole =
    roleOf(fileIndex.isInSourceContent(directory), name)

  private def roleOf(inSourceContent: Boolean, fileName: String): SourceRole =
    if (!inSourceContent) Untracked
    else
      // Extensions are matched case-insensitively by the platform, so `Foo.Scala` is a Scala file too.
      StringUtil.toLowerCase(FileUtilRt.getExtension(fileName)) match 
        case extension if CompiledExtensions.contains(extension) => Compiled
        case extension if OtherJvmSourceExtensions.contains(extension) => BuildInput
        case _ if generatesScala(fileName) => BuildInput
        case _ => Untracked

  /**
   * Template languages that generate Scala sources register themselves as [[ScalaLanguageDerivative]]s.
   * The build compiles what they generate, so a change to
   * one makes the outputs stale, while a scope would only ever hold the generated source.
   *
   * Asked by file name rather than by extension because these file types are registered by pattern
   * (`*.scala.html`) — which is why the `"scala.html"` entry in the set above never matched anything: the
   * extension of `index.scala.html` is `html`.
   */
  private def generatesScala(fileName: String): Boolean =
    FileTypeRegistry.getInstance().getFileTypeByFileName(fileName) match 
      case languageFileType: LanguageFileType => ScalaLanguageDerivative.existsFor(languageFileType)
      case _ => false