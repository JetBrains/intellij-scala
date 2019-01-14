package scala.meta
package intellij

import java.io.File
import java.util.jar.Attributes.{Name => AttributeName}

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.{Module, ModuleUtilCore}
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.io.JarUtil
import com.intellij.psi.{PsiElement, PsiFile}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationsHolder, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedInUserData, CachedWithRecursionGuard, ModCount}
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

package object psi {

  @Cached(ModificationTracker.NEVER_CHANGED, null)
  private[this] def isMetaParadiseJar(pathName: String): Boolean = {
    import JarUtil._
    new File(pathName) match {
      case file if containsEntry(file, "scalac-plugin.xml") =>
        def attribute(nameSuffix: String) =
          getJarAttribute(file, new AttributeName(s"Specification-$nameSuffix"))

        attribute("Vendor") == "org.scalameta" &&
          attribute("Title") == "paradise"
      case _ => false
    }
  }

  private implicit class ScalaPsiElementExt(val repr: PsiElement) extends AnyVal {

    def isMetaEnabled: Boolean = repr.getContainingFile match {
      case file: ScalaFile if !file.isCompiled => file.isMetaEnabled(repr.getProject)
      case _ => false
    }
  }

  implicit class PsiFileExt(val repr: PsiFile) extends AnyVal {

    def isMetaEnabled(project: Project): Boolean =
      !ScStubElementType.Processing &&
        !DumbService.isDumb(project) &&
        (ApplicationManager.getApplication.isUnitTestMode ||
          findModule.exists(hasMetaParadiseJar))

    private def findModule = Option {
      ModuleUtilCore.findModuleForFile(repr.getOriginalFile)
    }

    private def hasMetaParadiseJar(module: Module) = ScalaCompilerConfiguration.instanceIn(repr.getProject)
      .getSettingsForModule(module)
      .plugins.exists(isMetaParadiseJar)
  }

  implicit class AnnotationExt(val repr: ScAnnotation) extends AnyVal {

    def isMetaMacro: Boolean = repr.isMetaEnabled && cached

    @CachedInUserData(repr, ModCount.getBlockModificationCount)
    private def cached: Boolean = repr.constructor.reference.exists {
      case reference: ScStableCodeReferenceElementImpl =>
        val processor = new ResolveProcessor(reference.getKinds(incomplete = false), reference, reference.refName)
        reference.doResolve(processor).collect {
          case ScalaResolveResult(definition: ScTypeDefinition, _) => definition
          case ScalaResolveResult(constructor: ScPrimaryConstructor, _) => constructor.containingClass
        }.exists(hasInlineAnnotation.unapply)
      case _ => false
    }
  }

  implicit class AnnotationHolderExt(val repr: ScAnnotationsHolder) extends AnyVal {

    def metaExpand: Either[String, Tree] =
      if (repr.isMetaEnabled) cached
      else Left("")

    @CachedWithRecursionGuard(repr, Left("Recursive meta expansion"), ModCount.getBlockModificationCount)
    private def cached: Either[String, Tree] = {
      import ScalaProjectSettings.ScalaMetaMode.Enabled
      ScalaProjectSettings.getInstance(repr.getProject).getScalaMetaMode match {
        case Enabled =>
          repr.annotations.find(_.isMetaMacro)
            .toRight("No meta annotation")
            .flatMap(MetaExpansionsManager.runMetaAnnotation)
        case _ => Left("Meta expansions disabled in settings")
      }
    }
  }

  object hasInlineAnnotation {

    def unapply(definition: ScTemplateDefinition): Boolean =
      definition.members.exists {
        case member if hasInlineModifier(member) => true
        case holder: ScAnnotationsHolder => holder.hasAnnotation("scala.meta.internal.inline.inline")
        case _ => false
      }

    private def hasInlineModifier(member: ScMember) =
      member.getModifierList.findFirstChildByType(ScalaTokenTypes.kINLINE) != null
  }

}
