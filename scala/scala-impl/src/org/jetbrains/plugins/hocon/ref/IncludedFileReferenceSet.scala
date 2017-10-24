package org.jetbrains.plugins.hocon.ref

import java.{lang => jl, util => ju}

import com.intellij.openapi.roots._
import com.intellij.openapi.roots.impl.DirectoryIndex
import com.intellij.openapi.util.{Condition, TextRange}
import com.intellij.psi._
import com.intellij.psi.impl.source.resolve.reference.impl.providers.{FileReference, FileReferenceSet}
import org.jetbrains.plugins.hocon.CommonUtil._
import org.jetbrains.plugins.hocon.HoconConstants._

import scala.collection.JavaConverters._

/**
  * FileReferenceSet subclass that tries to simulate how Typesafe Config handles includes with its
  * default includer implementation, <tt>com.typesafe.config.impl.SimpleIncluder</tt> -
  * as much as this is possible without access to actual runtime.
  * <p/>
  * This implementation will only try to resolve includes with <tt>classpath(...)</tt> qualifier or no qualifier -
  * that is, <tt>file(...)</tt> and <tt>url(...)</tt> are not supported since they can only be understood at runtime.
  * Also, for heuristic includes (no qualifier) in source, resource or library files, it is assumed that including file
  * was loaded from classpath resource and thus, included path will be interpreted as classpath resource relative to
  * current file. If including file is neither in sources or library, heuristic include will only be resolved if the
  * path is relative and resolution will be done relative to including file's parent directory.
  * <p/>
  * Files to include will be searched for in classpath of including file's containing module or - when including file
  * is in a library - joined classpath of all modules that directly depend on that library. Test sources and dependencies
  * are searched only when including file is also a test source or lies in a test dependency.
  * <p/>
  * Just like Typesafe Config, this implementation will try to guess extension of included resource to be either
  * <tt>.conf</tt>, <tt>.json</tt> or <tt>.properties</tt>. It is impossible to include a file with any other extension.
  * This constraint is also reflected by appropriate completion filter.
  * <p/>
  * If a reference resolves to multiple files, they will be sorted so that .conf files come first, .json files after
  * them and .properties files at the end. This reflects the order in which Typesafe Config merges those files.
  */
class IncludedFileReferenceSet(text: String, element: PsiElement, forcedAbsolute: Boolean, fromClasspath: Boolean)
  extends FileReferenceSet(text, element, 1, null, true) {

  setEmptyPathAllowed(false)

  override def isAbsolutePathReference: Boolean =
    forcedAbsolute || super.isAbsolutePathReference

  override def couldBeConvertedTo(relative: Boolean): Boolean =
    if (relative) !forcedAbsolute
    else fromClasspath

  override def createFileReference(range: TextRange, index: Int, text: String): FileReference =
    new IncludedFileReference(this, range, index, text)

  override def getReferenceCompletionFilter: Condition[PsiFileSystemItem] =
    new Condition[PsiFileSystemItem] {
      def value(item: PsiFileSystemItem): Boolean = item.isDirectory ||
        item.getName.endsWith(ConfExt) || item.getName.endsWith(JsonExt) || item.getName.endsWith(PropsExt)
    }

  // code mostly based on similar bits in `FileReferenceSet` and `PsiFileReferenceHelper`
  override def computeDefaultContexts: ju.Collection[PsiFileSystemItem] = {
    val empty = ju.Collections.emptyList[PsiFileSystemItem]

    def single(fsi: PsiFileSystemItem) = ju.Collections.singletonList(fsi)

    val cf = getContainingFile
    if (cf == null) return empty

    val containingFile = Option(cf.getContext).map(_.getContainingFile).getOrElse(cf)

    val proj = containingFile.getProject
    val vfile = containingFile.getOriginalFile.getVirtualFile
    if (vfile == null) return empty

    val parent = vfile.getParent
    if (parent == null) return empty

    val psiManager = PsiManager.getInstance(proj)

    def classpathDefaultContexts: ju.Collection[PsiFileSystemItem] = {
      val empty = ju.Collections.emptyList[PsiFileSystemItem]

      val pfi = ProjectRootManager.getInstance(proj).getFileIndex
      val pkgName =
        if (isAbsolutePathReference) ""
        else pfi.getPackageNameByDirectory(parent)

      if (pkgName == null) return empty

      val allScopes = pfi.getOrderEntriesForFile(parent).iterator.asScala.collect {
        case msoe: ModuleSourceOrderEntry =>
          msoe.getOwnerModule.getModuleRuntimeScope(pfi.isInTestSourceContent(parent))
        case loe: LibraryOrderEntry =>
          loe.getOwnerModule.getModuleRuntimeScope(loe.getScope == DependencyScope.TEST)
      }

      def orderEntryScope = allScopes.reduceOption(_ union _)

      def moduleScope = Option(pfi.getModuleForFile(parent)).map(_.getModuleRuntimeScope(false))

      (orderEntryScope orElse moduleScope).map { scope =>
        // If there are any source roots with package prefix and that package is a subpackage of
        // including file's package, they will be omitted because `getDirectoriesByPackageName` doesn't find them.
        // I tried to fix this by manually searching for package-prefixed source dirs and representing them with
        // `PackagePrefixFileSystemItem` instances, but implementation of `FileReference#innerResolveInContext`
        // straight away negates my efforts by explicitly ignoring package prefixes - not sure why.
        // TODO: possibly fix this in some other way?
        DirectoryIndex.getInstance(proj).getDirectoriesByPackageName(pkgName, false).iterator.asScala
          .filter(scope.contains).flatMap(dir => Option(psiManager.findDirectory(dir)))
          .toJList[PsiFileSystemItem]

      } getOrElse empty
    }

    if (fromClasspath)
      classpathDefaultContexts
    else if (!isAbsolutePathReference)
      Option(psiManager.findDirectory(parent)).map(single).getOrElse(empty)
    else
      empty
  }

}

object IncludedFileReference {
  val ResolveResultOrdering = Ordering.by { rr: ResolveResult =>
    rr.getElement match {
      case file: PsiFile =>
        val name = file.getName
        if (name.endsWith(ConfExt)) 0
        else if (name.endsWith(JsonExt)) 1
        else if (name.endsWith(PropsExt)) 2
        else 3
      case _ =>
        3
    }
  }
}

class IncludedFileReference(refSet: FileReferenceSet, range: TextRange, index: Int, text: String)
  extends FileReference(refSet, range, index, text) {

  private def lacksExtension(text: String) =
    isLast && text.nonEmpty && text != "." && text != ".." && text != "/" &&
      !text.endsWith(ConfExt) && !text.endsWith(JsonExt) && !text.endsWith(PropsExt)

  override def innerResolve(caseSensitive: Boolean, containingFile: PsiFile): Array[ResolveResult] = {
    val result = super.innerResolve(caseSensitive, containingFile)

    // Sort the files so that .conf files come first, .json files after then and .properties files at the end
    // This is to mimic Typesafe Config which merges included files in exactly that order.
    ju.Arrays.sort(result, IncludedFileReference.ResolveResultOrdering)
    result
  }

  override def innerResolveInContext(text: String, context: PsiFileSystemItem, result: ju.Collection[ResolveResult],
                                     caseSensitive: Boolean): Unit =
    if (lacksExtension(text)) {
      def resolveWithExt(ext: String) =
        super.innerResolveInContext(text + ext, context, result, caseSensitive)

      resolveWithExt(ConfExt)
      resolveWithExt(JsonExt)
      resolveWithExt(PropsExt)
    } else
      super.innerResolveInContext(text, context, result, caseSensitive)

  override def getFileNameToCreate: String =
    if (lacksExtension(getCanonicalText))
      super.getFileNameToCreate + ConfExt
    else
      super.getFileNameToCreate
}
