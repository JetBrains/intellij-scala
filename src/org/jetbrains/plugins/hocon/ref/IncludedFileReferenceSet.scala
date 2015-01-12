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
 * Also, for heuristic include (no qualifier), it is assumed that including file was loaded from classpath resource
 * and thus, included path will be interpreted as classpath resource relative to current file.
 * <p/>
 * Files to include will be searched for in classpath of including file's containing module or - when including file
 * is in a library - joined classpath of all modules that directly depend on that library. Test sources and dependencies
 * are searched only when including file is also a test source or lies in a test dependency.
 * <p/>
 * Just like Typesafe Config, this implementation will try to guess extension of included resource to be either
 * <tt>.conf</tt>, <tt>.json</tt> or <tt>.properties</tt>. It is impossible to include a file with any other extension.
 * This constraint is also reflected by appropriate completion filter.
 */
class IncludedFileReferenceSet(text: String, element: PsiElement, absolute: Boolean)
        extends FileReferenceSet(text, element, 1, null, true) {

  setEmptyPathAllowed(false)

  override def isAbsolutePathReference: Boolean =
    absolute || super.isAbsolutePathReference

  override def couldBeConvertedTo(relative: Boolean): Boolean =
    !(relative && absolute)

  override def createFileReference(range: TextRange, index: Int, text: String): FileReference =
    new IncludedFileReference(this, range, index, text)

  override def getReferenceCompletionFilter: Condition[PsiFileSystemItem] =
    new Condition[PsiFileSystemItem] {
      def value(item: PsiFileSystemItem): Boolean = item.isDirectory ||
              item.getName.endsWith(ConfExt) || item.getName.endsWith(JsonExt) || item.getName.endsWith(PropsExt)
    }

  override def computeDefaultContexts: ju.Collection[PsiFileSystemItem] = {
    // code mostly based on similar bits in `FileReferenceSet` and `PsiFileReferenceHelper`

    val empty = ju.Collections.emptyList[PsiFileSystemItem]
    val cf = getContainingFile
    if (cf == null) return empty

    val containingFile = Option(cf.getContext).map(_.getContainingFile).getOrElse(cf)

    val proj = containingFile.getProject
    val vfile = containingFile.getOriginalFile.getVirtualFile
    if (vfile == null) return empty

    val parent = vfile.getParent
    if (parent == null) return empty

    val psiManager = PsiManager.getInstance(proj)

    val pfi = ProjectRootManager.getInstance(proj).getFileIndex
    val pkgName =
      if (isAbsolutePathReference) ""
      else pfi.getPackageNameByDirectory(parent)

    if (pkgName == null) return empty

    val scope = pfi.getOrderEntriesForFile(parent).iterator.asScala.map { oe =>
      val withTests = pfi.isInTestSourceContent(parent) || (oe match {
        case eoe: ExportableOrderEntry => eoe.getScope == DependencyScope.TEST
        case _ => false
      })
      oe.getOwnerModule.getModuleWithDependenciesAndLibrariesScope(withTests)
    }.reduce(_ union _)

    // If there are any source roots with package prefix and that package is a subpackage of
    // including file's package, they will be omitted because `getDirectoriesByPackageName` doesn't find them.
    // I tried to fix this by manually searching for package-prefixed source dirs and representing them with
    // `PackagePrefixFileSystemItem` instances, but implementation of `FileReference#innerResolveInContext`
    // straight away negates my efforts by explicitly ignoring package prefixes - not sure why.
    // TODO: possibly fix this in some other way?
    DirectoryIndex.getInstance(proj).getDirectoriesByPackageName(pkgName, false).iterator.asScala
            .filter(scope.contains).flatMap(dir => Option(psiManager.findDirectory(dir)))
            .toJList
  }

}

class IncludedFileReference(refSet: FileReferenceSet, range: TextRange, index: Int, text: String)
        extends FileReference(refSet, range, index, text) {

  private def lacksExtension(text: String) =
    isLast && text.nonEmpty && text != "." && text != ".." && text != "/" &&
            !text.endsWith(ConfExt) && !text.endsWith(JsonExt) && !text.endsWith(PropsExt)


  override def innerResolveInContext(text: String, context: PsiFileSystemItem, result: ju.Collection[ResolveResult],
                                     caseSensitive: Boolean) =
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
