package org.jetbrains.plugins.scala
package lang
package psi
package compiled

import com.intellij.lang.Language
import com.intellij.openapi.roots.{OrderRootType, ProjectRootManager, impl => rootsImpl}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiClassOwner, PsiElement, PsiFileSystemItem, PsiManager, search}
import com.intellij.util.Processor

import scala.annotation.tailrec

private final class ScClsFileViewProvider(sourceName: String,
                                          override val getContents: String,
                                          eventSystemEnabled: Boolean)
                                         (implicit manager: PsiManager, file: VirtualFile)
  extends impl.ScFileViewProviderFactory.ScFileViewProvider(eventSystemEnabled) {

  //noinspection TypeAnnotation
  override def createFile(language: Language) =
    new impl.ScalaFileImpl(this) {

      import search.GlobalSearchScope

      override def isCompiled: Boolean = true

      override def getVirtualFile: VirtualFile = ScClsFileViewProvider.this.getVirtualFile

      override def getNavigationElement: PsiElement =
        findSourceForCompiledFile(getVirtualFile).flatMap { file =>
          Option(getManager.findFile(file))
        }.getOrElse {
          super.getNavigationElement
        }

      import macroAnnotations.CachedInUserData

      @CachedInUserData(this, ProjectRootManager.getInstance(getProject))
      override protected def defaultFileResolveScope(file: VirtualFile): GlobalSearchScope =
      // this cache is very inefficient when orderEntries.size is large
        rootsImpl.LibraryScopeCache.getInstance(getProject).getLibraryScope {
          orderEntries(file)
        }

      @CachedInUserData(this, ProjectRootManager.getInstance(getProject))
      private def findSourceForCompiledFile(file: VirtualFile): Option[VirtualFile] = findInLibSources(file) match {
        case result: Some[VirtualFile] => result
        case _ =>
          //Look in libraries sources if file not relative to path
          typeDefinitions.headOption match {
            case Some(typeDefinition) =>
              val qualifiedName = typeDefinition.qualifiedName
              var result = Option.empty[VirtualFile]

              val processor = new Processor[PsiFileSystemItem] {
                override def process(item: PsiFileSystemItem): Boolean = {
                  val sourceFile = item.getVirtualFile
                  val clazzIterator = getManager.findFile(sourceFile) match {
                    case scalaFile: api.ScalaFile => scalaFile.typeDefinitions.iterator
                    case classOwner: PsiClassOwner => classOwner.getClasses.iterator
                    case _ => Iterator.empty
                  }

                  import extensions.PsiClassExt
                  while (clazzIterator.hasNext) {
                    if (qualifiedName == clazzIterator.next().qualifiedName) {
                      result = Some(sourceFile)
                      return false
                    }
                  }

                  true
                }
              }

              search.FilenameIndex.processFilesByName(
                sourceName,
                false,
                processor,
                GlobalSearchScope.allScope(getProject),
                getProject,
                null
              )

              result
            case _ => None
          }
      }

      private def findInLibSources(file: VirtualFile): Option[VirtualFile] = {
        val relPath = packagePath match {
          case "" => sourceName
          case path => s"$path/$sourceName"
        }

        // Look in libraries' sources
        val entryIterator = orderEntries(file).iterator
        while (entryIterator.hasNext) {
          val filesIterator = entryIterator.next()
            .getFiles(OrderRootType.SOURCES)
            .iterator

          while (filesIterator.hasNext) {
            filesIterator.next().findFileByRelativePath(relPath) match {
              case null =>
              case source if getManager.findFile(source).isInstanceOf[PsiClassOwner] => return Some(source)
              case _ =>
            }
          }
        }

        None
      }

      private def orderEntries(file: VirtualFile) =
        ProjectRootManager.getInstance(getProject).getFileIndex.getOrderEntriesForFile(file)

      private def packagePath: String = {
        @tailrec
        def inner(packaging: api.toplevel.ScPackaging, result: StringBuilder): String = {
          result.append(packaging.packageName)
          packaging.packagings.headOption match {
            case Some(implicitPackaging) if !implicitPackaging.isExplicit => inner(implicitPackaging, result.append("/"))
            case _ => result.toString
          }
        }

        val packageName = firstPackaging match {
          case Some(packaging) if !packaging.isExplicit => inner(packaging, StringBuilder.newBuilder)
          case _ => ""
        }

        packageName + typeDefinitions.find(_.isPackageObject)
          .fold("") { definition =>
            (if (packageName.length > 0) "/" else "") + definition.name
          }
      }

    }

  override protected def createCopy(eventSystemEnabled: Boolean)
                                   (implicit manager: PsiManager, file: VirtualFile) =
    new ScClsFileViewProvider(sourceName, getContents, eventSystemEnabled)
}