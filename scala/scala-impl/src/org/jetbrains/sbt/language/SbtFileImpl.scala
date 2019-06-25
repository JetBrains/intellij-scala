package org.jetbrains.sbt
package language

import com.intellij.openapi.module.{Module, ModuleManager, ModuleUtilCore}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi._
import com.intellij.psi.search.{GlobalSearchScope, searches}
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.ScDeclarationSequenceHolder
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl._
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedInUserData, ModCount}
import org.jetbrains.sbt.project.data.SbtModuleData
import org.jetbrains.sbt.project.module.SbtModule.{Build, Imports}

import scala.collection.JavaConverters

/**
 * @author Pavel Fatin
 */
final class SbtFileImpl private[language](provider: FileViewProvider)
  extends ScalaFileImpl(provider, SbtFileType)
    with ScDeclarationSequenceHolder {

  import SbtFileImpl._

  override def isScriptFileImpl: Boolean = false

  override def typeDefinitions: Seq[ScTypeDefinition] = Seq.empty

  override val allowsForwardReferences: Boolean = true

  override def processDeclarations(processor: scope.PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean =
    super[ScalaFileImpl].processDeclarations(processor, state, lastParent, place) &&
      super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place) &&
      syntheticFile.forall { file =>
        file.processDeclarations(processor, state, file.getLastChild, place)
      }

  @Cached(ModCount.getModificationCount, this)
  private def syntheticFile: Option[ScalaFile] = {
    implicit val manager: ScalaPsiManager = ScalaPsiManager.instance(getProject)
    val imports = TargetModule(this).imports.map {
      // TODO this is a workaround, we need to find out why references stopped resolving via the chained imports
      case "Keys._" => "sbt.Keys._"
      case "Build._" => "sbt.Build._"
      // TODO: this is a workaround. `processDeclarations` does not resolve "Play.autoImport -> PlayImport"
      //    However, when object with implicit imports is located in the same file where plugin object resides
      //    everything is resolved, but PlayImport and Play are in different files.
      case "_root_.play.Play.autoImport._" => "_root_.play.PlayImport._"
      case importText => importText
    }

    if (imports.isEmpty) None
    else Some(ScalaPsiElementFactory.createScalaFileFromText(imports.mkString("import ", ", ", ";")))
  }

  @CachedInUserData(this, ProjectRootManager.getInstance(getProject))
  override def getFileResolveScope: GlobalSearchScope = TargetModule(this) match {
    case SbtModuleWithScope(_, moduleWithDependenciesAndLibrariesScope) => moduleWithDependenciesAndLibrariesScope
    case _ => super.getFileResolveScope
  }
}

object SbtFileImpl {

  private sealed trait TargetModule {
    def module: Module
  }

  private object TargetModule {

    def apply(file: SbtFileImpl): TargetModule =
      ModuleUtilCore.findModuleForPsiElement(file) match {
        case null => ModuleLess
        case module =>
          val manager = ModuleManager.getInstance(file.getProject)

          val moduleByUri = for {
            SbtModuleData(_, buildURI) <- SbtUtil.getSbtModuleData(module)

            module <- manager.getModules.find { module =>
              Build(module) == buildURI
            }
          } yield module

          moduleByUri.orElse {
            // TODO remove in 2018.3+
            // this is the old way of finding a build module which breaks if the way the module name is assigned changes
            Option(manager.findModuleByName(module.getName + Sbt.BuildModuleSuffix))
          }.fold(DefinitionModule(module): TargetModule) { module =>
            SbtModuleWithScope(module, module.getModuleWithDependenciesAndLibrariesScope(false))
          }
      }

    implicit class Ext(private val module: TargetModule) extends AnyVal {

      def imports(implicit manager: ScalaPsiManager): Seq[String] = module match {
        case ModuleLess => Seq.empty
        case DefinitionModule(module) => Imports(module)
        case SbtModuleWithScope(module, moduleWithDependenciesAndLibrariesScope) =>
          import JavaConverters._

          val moduleScope = module.getModuleScope
          val localObjectsWithDefinitions = for {
            fqn <- Sbt.DefinitionHolderClasses
            aClass <- manager.getCachedClasses(moduleWithDependenciesAndLibrariesScope, fqn)

            inheritor <- searches.ClassInheritorsSearch
              .search(aClass, moduleScope, true)
              .findAll
              .asScala
          } yield s"${inheritor.qualifiedName}._"

          Imports(module) ++ localObjectsWithDefinitions
      }
    }
  }

  private case object ModuleLess extends TargetModule {
    override def module: Nothing = throw new NoSuchElementException("Module not found")
  }

  private case class DefinitionModule(module: Module) extends TargetModule

  private case class SbtModuleWithScope(module: Module,
                                        moduleWithDependenciesAndLibrariesScope: GlobalSearchScope) extends TargetModule
}
