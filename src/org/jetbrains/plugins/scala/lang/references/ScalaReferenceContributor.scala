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
import com.intellij.openapi.diagnostic.Logger
import extensions.toPsiNamedElementExt

class ScalaReferenceContributor extends PsiReferenceContributor {

  def registerReferenceProviders(registrar: PsiReferenceRegistrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(classOf[ScLiteral]), new FilePathReferenceProvider())
  }
}

// todo: Copy of the corresponding class from IDEA, changed to use ScLiteral rather than PsiLiteralExpr
class FilePathReferenceProvider extends PsiReferenceProvider {
  private val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.lang.references.FilePathReferenceProvider")

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
    for (module <- JavaConversions.asScalaIterable(modules)) {
      moduleRootManager = ModuleRootManager.getInstance(module)
      val sourceRoots: Array[VirtualFile] = moduleRootManager.getSourceRoots
      for (root <- sourceRoots) {
        val directory: PsiDirectory = psiManager.findDirectory(root)
        if (directory != null) {
          val aPackage: PsiPackage = JavaDirectoryService.getInstance.getPackage(directory)
          if (aPackage != null && aPackage.name != null) {
            try {
              val createMethod = Class.forName("com.intellij.psi.impl.source.resolve.reference.impl.providers.PackagePrefixFileSystemItem").getMethod("create", classOf[PsiDirectory])
              createMethod.invoke(directory)
            } catch {
              case t  => LOG.warn(t)
            }
          }
          else {
            result.add(directory)
          }
        }
      }
    }
    result
  }

  @NotNull def getReferencesByElement(element: PsiElement, text: String, offset: Int, soft: Boolean): Array[PsiReference] = {
    new FileReferenceSet(text, element, offset, this, true, myEndingSlashNotAllowed) {
      protected override def isSoft: Boolean = {
        soft
      }

      override def isAbsolutePathReference: Boolean = {
        true
      }

      override def couldBeConvertedTo(relative: Boolean): Boolean = {
        !relative
      }

      override def absoluteUrlNeedsStartSlash: Boolean = {
        val s: String = getPathString
        s != null && s.length > 0 && s.charAt(0) == '/'
      }

      @NotNull override def computeDefaultContexts: java.util.Collection[PsiFileSystemItem] = {
        val module: Module = ModuleUtil.findModuleForPsiElement(getElement)
        getRoots(module, true)
      }

      override def createFileReference(range: TextRange, index: Int, text: String): FileReference = {
        FilePathReferenceProvider.this.createFileReference(this, range, index, text)
      }

      protected override def getReferenceCompletionFilter: Condition[PsiFileSystemItem] = {
        new Condition[PsiFileSystemItem] {
          def value(element: PsiFileSystemItem): Boolean = {
            isPsiElementAccepted(element)
          }
        }
      }
    }.getAllReferences.map(x => x)
  }

  override def acceptsTarget(@NotNull target: PsiElement): Boolean = {
    target.isInstanceOf[PsiFileSystemItem]
  }

  protected def isPsiElementAccepted(element: PsiElement): Boolean = {
    !(element.isInstanceOf[PsiJavaFile] && element.isInstanceOf[PsiCompiledElement])
  }

  protected def createFileReference(referenceSet: FileReferenceSet, range: TextRange, index: Int, text: String): FileReference = {
    new FileReference(referenceSet, range, index, text)
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
    getReferencesByElement(element, text, 1, true)
  }

  private final val myEndingSlashNotAllowed: Boolean = false
}

