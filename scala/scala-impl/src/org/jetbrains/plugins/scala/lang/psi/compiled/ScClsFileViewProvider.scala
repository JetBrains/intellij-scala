package org.jetbrains.plugins.scala.lang.psi.compiled

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.LibraryScopeCache
import com.intellij.openapi.roots.{OrderEntry, ProjectFileIndex, ProjectRootManager}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.{FilenameIndex, GlobalSearchScope}
import com.intellij.psi.{PsiClassOwner, PsiElement, PsiManager, SingleRootFileViewProvider}
import com.intellij.util.CommonProcessors.FindProcessor
import org.jetbrains.plugins.scala.caches.cachedInUserData
import org.jetbrains.plugins.scala.extensions.{ClassQualifiedName, ObjectExt, PsiClassExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl

import java.{util => ju}
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

final class ScClsFileViewProvider(decompilationResult: ScalaDecompilationResult)
                                 (manager: PsiManager, file: VirtualFile, eventSystemEnabled: Boolean, language: Language)
  extends SingleRootFileViewProvider(manager, file, eventSystemEnabled, language) {

  private def sourceName: String = decompilationResult.sourceName

  override def getContents: String = decompilationResult.sourceText

  override def createFile(project: Project,
                          file: VirtualFile,
                          fileType: FileType) =
    new ScClsFileViewProvider.ScClsFileImpl(this)

  override def createCopy(file: VirtualFile) =
    new ScClsFileViewProvider(decompilationResult)(getManager, file, eventSystemEnabled = false, getBaseLanguage)
}

object ScClsFileViewProvider {

  final class ScClsFileImpl(override val getViewProvider: ScClsFileViewProvider)
    extends ScalaFileImpl(getViewProvider) {

    private implicit def manager: PsiManager = getManager

    override def isCompiled: Boolean = true

    override def getVirtualFile: VirtualFile = getViewProvider.getVirtualFile

    override def getNavigationElement: PsiElement = {
      val sourceForCompiledFile: Option[VirtualFile] = cachedInUserData("getNavigationElement.sourceForCompiledFile", this, ProjectRootManager.getInstance(getProject)) {
        findSourceByRelativePath.orElse(findSourceByQualifiedName)
      }

      sourceForCompiledFile
        .flatMap(findPsiFile)
        .getOrElse(super.getNavigationElement)
    }

    override protected def defaultFileResolveScope(file: VirtualFile): GlobalSearchScope = cachedInUserData("defaultFileResolveScope",  this, ProjectRootManager.getInstance(getProject), Tuple1(file)) {
      // this cache is very inefficient when orderEntries.size is large
      LibraryScopeCache.getInstance(manager.getProject)
        .getLibraryScope(orderEntries(file))
    }

    def findSourceByRelativePath: Option[VirtualFile] = {
      val relPath = relativePath(typeDefinitions)
      val classOrderEntries = orderEntries(getVirtualFile)

      findSourceFileWithName(getViewProvider.sourceName) {
        hasSameRelativePathInSources(_, relPath, classOrderEntries)
      }
    }

    //Look in libraries sources if file not relative to path
    //this is more expensive because parsing of found source files is required
    private def findSourceByQualifiedName: Option[VirtualFile] = {
      val qualifiedName = typeDefinitions.headOption match {
        case Some(ClassQualifiedName(qualifiedName)) => qualifiedName
        case _                                       => return None
      }

      findSourceFileWithName(getViewProvider.sourceName) {
        hasClassWithQualifiedName(_, qualifiedName)
      }
    }

    private def relativePath(typeDefinitions: Seq[ScTypeDefinition]) = {
      val builder = new StringBuilder()

      buildPackagePath(firstPackaging, builder)

      for {
        packageObject <- typeDefinitions.find(_.isPackageObject)
        segment = packageObject.name
      } builder.appendSegment(segment)

      builder.append(getViewProvider.sourceName).toString
    }

    private def projectFileIndex: ProjectFileIndex = ProjectFileIndex.getInstance(manager.getProject)

    @tailrec
    private def buildPackagePath(maybePackaging: Option[ScPackaging], builder: StringBuilder): StringBuilder =
      maybePackaging match {
        case Some(packaging) if !packaging.isExplicit =>
          buildPackagePath(
            packaging.packagings.headOption,
            builder.appendSegments(packaging.packageName.split('.').toSeq)
          )
        case _ => builder
      }

    private def hasClassWithQualifiedName(sourceFile: VirtualFile, qualifiedName: String): Boolean = {
      val clazzIterator = findPsiFile(sourceFile) match {
        case Some(scalaFile: ScalaFile) => scalaFile.typeDefinitions.iterator
        case Some(classOwner: PsiClassOwner) => classOwner.getClasses.iterator
        case _ => Iterator.empty
      }

      clazzIterator.exists(_.qualifiedName == qualifiedName)
    }

    private def hasSameRelativePathInSources(sourceFile: VirtualFile,
                                             relPath: String,
                                             classFileEntries: ju.List[OrderEntry]): Boolean =
      sourceFile.getPath.endsWith(relPath) && haveSameEntry(orderEntries(sourceFile), classFileEntries)

    //noinspection ExistsEquals
    private def haveSameEntry(firstEntries: ju.List[OrderEntry], secondEntries: ju.List[OrderEntry]): Boolean = {
      firstEntries.asScala.exists { e1 =>
        secondEntries.asScala.exists { e2 =>
          e1 == e2
        }
      }
    }

    private def findSourceFileWithName(name: String)
                                      (predicate: VirtualFile => Boolean): Option[VirtualFile] = {
      val project = getProject
      val fileIndex = projectFileIndex
      val sourcesScope = new GlobalSearchScope(project) {
        override def isSearchInModuleContent(aModule: Module): Boolean = false
        override def isSearchInLibraries: Boolean = true
        override def contains(file: VirtualFile): Boolean = fileIndex.isInSource(file)
      }
      val processor: FindProcessor[VirtualFile] = item => !item.isDirectory && predicate(item)

      val caseSensitively = true
      FilenameIndex.processFilesByName(
        name,
        caseSensitively,
        GlobalSearchScope.allScope(project).intersectWith(sourcesScope),
        processor,
      )
      Option(processor.getFoundValue)
    }

    private def orderEntries(file: VirtualFile) =
      projectFileIndex.getOrderEntriesForFile(file)

    private def findPsiFile(file: VirtualFile): Option[PsiClassOwner] =
      manager.findFile(file).asOptionOf[PsiClassOwner]
  }

  private implicit class StringBuilderExt(private val builder: StringBuilder) extends AnyVal {

    def appendSegment(segment: String): StringBuilder =
      builder.append(segment).append('/')

    def appendSegments(segments: Seq[String]): StringBuilder = {
      segments.foreach(appendSegment)
      builder
    }
  }

}