package org.jetbrains.plugins.scala
package lang
package references

import com.intellij.patterns.PlatformPatterns
import psi.api.base.ScLiteral
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.util.containers.ContainerUtil
import com.intellij.openapi.module.{ModuleUtil, Module}
import com.intellij.util.ProcessingContext
import java.util.{ArrayList, Collections}
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.util.{Condition, TextRange}
import org.jetbrains.annotations.NotNull
import collection.JavaConversions
import com.intellij.psi._
import impl.source.resolve.reference.impl.providers.{FileReference, FileReferenceSet}

class ScalaReferenceContributor extends PsiReferenceContributor {

  def registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(classOf[ScLiteral]), new FilePathReferenceProvider())
  }
}

// Copy of the corresponding class from IDEA, changed to use ScLiteral rather than PsiLiteralExpr
class FilePathReferenceProvider extends PsiReferenceProvider {
  @NotNull def getRoots(thisModule: Module, includingClasses: Boolean): java.util.Collection[PsiFileSystemItem] = {
    if (thisModule == null) return Collections.emptyList[PsiFileSystemItem]
    val modules: java.util.List[Module] = new ArrayList[Module]
    modules.add(thisModule)
    var moduleRootManager: ModuleRootManager = ModuleRootManager.getInstance(thisModule)
    ContainerUtil.addAll(modules, moduleRootManager.getDependencies: _*)
    val result: java.util.List[PsiFileSystemItem] = new java.util.ArrayList[PsiFileSystemItem]
    val psiManager: PsiManager = PsiManager.getInstance(thisModule.getProject)
    if (includingClasses) {
      val libraryUrls: Array[VirtualFile] = moduleRootManager.orderEntries.getAllLibrariesAndSdkClassesRoots
      for (file <- libraryUrls) {
        val directory: PsiDirectory = psiManager.findDirectory(file)
        if (directory != null) {
          result.add(directory)
        }
      }
    }
    for (module <- JavaConversions.asIterable(modules)) {
      moduleRootManager = ModuleRootManager.getInstance(module)
      val sourceRoots: Array[VirtualFile] = moduleRootManager.getSourceRoots
      for (root <- sourceRoots) {
        val directory: PsiDirectory = psiManager.findDirectory(root)
        if (directory != null) {
          val aPackage: PsiPackage = JavaDirectoryService.getInstance.getPackage(directory)
          if (aPackage != null && aPackage.getName != null) {
//            result.add(PackagePrefixFileSystemItem.create(directory)) // TODO
          }
          else {
            result.add(directory)
          }
        }
      }
    }
    return result
  }

  @NotNull def getReferencesByElement(element: PsiElement, text: String, offset: Int, soft: Boolean): Array[PsiReference] = {
    return new FileReferenceSet(text, element, offset, this, true, myEndingSlashNotAllowed) {
      protected override def isSoft: Boolean = {
        return soft
      }

      override def isAbsolutePathReference: Boolean = {
        return true
      }

      override def couldBeConvertedTo(relative: Boolean): Boolean = {
        return !relative
      }

      override def absoluteUrlNeedsStartSlash: Boolean = {
        val s: String = getPathString
        return s != null && s.length > 0 && s.charAt(0) == '/'
      }

      @NotNull override def computeDefaultContexts: java.util.Collection[PsiFileSystemItem] = {
        val module: Module = ModuleUtil.findModuleForPsiElement(getElement)
        return getRoots(module, true)
      }

      override def createFileReference(range: TextRange, index: Int, text: String): FileReference = {
        return FilePathReferenceProvider.this.createFileReference(this, range, index, text)
      }

      protected override def getReferenceCompletionFilter: Condition[PsiFileSystemItem] = {
        return new Condition[PsiFileSystemItem] {
          def value(element: PsiFileSystemItem): Boolean = {
            return isPsiElementAccepted(element)
          }
        }
      }
    }.getAllReferences.map(x => x)
  }

  override def acceptsTarget(@NotNull target: PsiElement): Boolean = {
    return target.isInstanceOf[PsiFileSystemItem]
  }

  protected def isPsiElementAccepted(element: PsiElement): Boolean = {
    return !(element.isInstanceOf[PsiJavaFile] && element.isInstanceOf[PsiCompiledElement])
  }

  protected def createFileReference(referenceSet: FileReferenceSet, range: TextRange, index: Int, text: String): FileReference = {
    return new FileReference(referenceSet, range, index, text)
  }

  @NotNull def getReferencesByElement(@NotNull element: PsiElement, @NotNull context: ProcessingContext): Array[PsiReference] = {
    var text: String = null
    if (element.isInstanceOf[ScLiteral]) {
      val value = (element.asInstanceOf[ScLiteral]).getValue
      if (value.isInstanceOf[String]) {
        text = value.asInstanceOf[String]
      }
    }
    if (text == null) return PsiReference.EMPTY_ARRAY
    return getReferencesByElement(element, text, 1, true)
  }

  private final val myEndingSlashNotAllowed: Boolean = false
}

