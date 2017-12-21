package scala.meta.intellij

import java.io.File
import java.util.jar.Attributes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.io.JarUtil
import com.intellij.psi.{PsiFile, ResolveResult}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScStubElementType
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor
import org.jetbrains.plugins.scala.macroAnnotations.Cached
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.extensions.ToNullSafe

object IdeaUtil {
  def safeAnnotationResolve(annotation: ScAnnotation): Option[ResolveResult] = {
    if (ScStubElementType.isStubBuilding || DumbService.isDumb(annotation.getProject))
      return None
    annotation.constructor.reference.flatMap {
      case stRef: ScStableCodeReferenceElementImpl =>
        val processor = new ResolveProcessor(stRef.getKinds(incomplete = false), stRef, stRef.refName)
        stRef.doResolve(processor).headOption
      case _ => None
    }
  }

  def inModuleWithParadisePlugin(file: PsiFile): Boolean = {
    if (ApplicationManager.getApplication.isUnitTestMode) true
    else {
      val project = file.getProject
      val plugins =
        file.getVirtualFile.nullSafe
          .map(ModuleUtilCore.findModuleForFile(_, project))
          .map(ScalaCompilerConfiguration.instanceIn(project).getSettingsForModule)
          .map(_.plugins)
          .getOrElse(Seq.empty)

      plugins.exists(isMetaParadiseJar)
    }
  }

  @Cached(ModificationTracker.NEVER_CHANGED, null)
  private def isMetaParadiseJar(path: String): Boolean = {
    val file = new File(path)
    if (!JarUtil.containsEntry(file, "scalac-plugin.xml")) return false

    val vendor = new Attributes.Name("Specification-Vendor")
    val title = new Attributes.Name("Specification-Title")

    JarUtil.getJarAttribute(file, vendor) == "org.scalameta" &&
      JarUtil.getJarAttribute(file, title) == "paradise"
  }
}
