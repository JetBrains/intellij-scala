package org.jetbrains.plugins.scala
package lang
package psi
package compiled

import java.{util => ju}

import com.intellij.lang.Language
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.LibraryScopeCache
import com.intellij.openapi.roots.{OrderEntry, OrderRootType, ProjectRootManager}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.{FilenameIndex, GlobalSearchScope}
import com.intellij.psi.{PsiClassOwner, PsiElement, PsiFileSystemItem, PsiManager, SingleRootFileViewProvider}
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.extensions.{ClassQualifiedName, PsiClassExt}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

import scala.annotation.tailrec

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

    import ScClsFileImpl._

    override def isCompiled: Boolean = true

    override def getVirtualFile: VirtualFile = getViewProvider.getVirtualFile

    override def getNavigationElement: PsiElement =
      findSourceForCompiledFile.flatMap { file =>
        Option(getManager.findFile(file))
      }.getOrElse {
        super.getNavigationElement
      }

    @CachedInUserData(this, ProjectRootManager.getInstance(getProject))
    override protected def defaultFileResolveScope(file: VirtualFile): GlobalSearchScope = {
      implicit val manager: PsiManager = getManager

      // this cache is very inefficient when orderEntries.size is large
      LibraryScopeCache.getInstance(manager.getProject)
        .getLibraryScope(orderEntries(file))
    }

    @CachedInUserData(this, ProjectRootManager.getInstance(getProject))
    private def findSourceForCompiledFile: Option[VirtualFile] = {
      implicit val manager: PsiManager = getManager
      val typeDefinitions = this.typeDefinitions

      findInLibSources(
        orderEntries(getVirtualFile),
        relativePath(typeDefinitions)
      ).orElse {
        //Look in libraries sources if file not relative to path

        typeDefinitions.headOption match {
          case Some(ClassQualifiedName(qualifiedName)) =>
            var result = Option.empty[VirtualFile]

            processFilesByName(getViewProvider.sourceName) { item =>
              val sourceFile = item.getVirtualFile
              val found = findInSources(sourceFile, qualifiedName)
              if (found) {
                result = Some(sourceFile)
              }

              !found
            }

            result
          case _ => None
        }
      }
    }

    private def relativePath(typeDefinitions: Seq[ScTypeDefinition]) = {
      val builder = StringBuilder.newBuilder

      buildPackagePath(firstPackaging, builder)

      for {
        packageObject <- typeDefinitions.find(_.isPackageObject)
        segment = packageObject.name
      } builder.appendSegment(segment)

      builder.append(getViewProvider.sourceName).toString
    }
  }

  private object ScClsFileImpl {

    @tailrec
    private def buildPackagePath(maybePackaging: Option[ScPackaging], builder: StringBuilder): StringBuilder =
      maybePackaging match {
        case Some(packaging) if !packaging.isExplicit =>
          buildPackagePath(
            packaging.packagings.headOption,
            builder.appendSegment(packaging.packageName)
          )
        case _ => builder
      }

    private def findInLibSources(entries: ju.List[OrderEntry], relPath: String)
                                (implicit manager: PsiManager): Option[VirtualFile] = {
      val entriesIterator = entries.iterator

      while (entriesIterator.hasNext) {
        val filesIterator: Iterator[VirtualFile] = ReadAction.compute(() => entriesIterator.next()
          .getFiles(OrderRootType.SOURCES)
          .iterator)

        while (filesIterator.hasNext) {
          filesIterator.next().findFileByRelativePath(relPath) match {
            case null =>
            case sourceFile if findFile(sourceFile).isInstanceOf[PsiClassOwner] => return Some(sourceFile)
            case _ =>
          }
        }
      }

      None
    }

    private def findInSources(sourceFile: VirtualFile, qualifiedName: String)
                             (implicit manager: PsiManager) = {
      val clazzIterator = findFile(sourceFile) match {
        case scalaFile: ScalaFile => scalaFile.typeDefinitions.iterator
        case classOwner: PsiClassOwner => classOwner.getClasses.iterator
        case _ => Iterator.empty
      }

      clazzIterator.exists(_.qualifiedName == qualifiedName)
    }

    private def processFilesByName(name: String)
                                  (processor: Processor[PsiFileSystemItem])
                                  (implicit manager: PsiManager) = {
      val project = manager.getProject
      FilenameIndex.processFilesByName(
        name,
        false,
        processor,
        GlobalSearchScope.allScope(project),
        project,
        null
      )
    }

    private def orderEntries(file: VirtualFile)
                            (implicit manager: PsiManager) =
      ProjectRootManager.getInstance(manager.getProject)
        .getFileIndex
        .getOrderEntriesForFile(file)

    private def findFile(file: VirtualFile)
                        (implicit manager: PsiManager) =
      manager.findFile(file)

    private implicit class StringBuilderExt(private val builder: StringBuilder) extends AnyVal {

      def appendSegment(segment: String): StringBuilder =
        builder.append(segment).append('/')
    }

  }

}