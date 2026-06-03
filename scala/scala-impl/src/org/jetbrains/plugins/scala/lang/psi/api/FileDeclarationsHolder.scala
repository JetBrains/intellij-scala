package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.impl.migration.PsiMigrationManager
import com.intellij.psi.scope.{NameHint, PsiScopeProcessor}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaLowerCase
import org.jetbrains.plugins.scala.extensions.{StubBasedExt, _}
import org.jetbrains.plugins.scala.externalLibraries.bm4.BetterMonadicForSupport
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.KindProjectorUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScPackageLike.processPackageObject
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl._
import org.jetbrains.plugins.scala.lang.psi.{ScDeclarationSequenceHolder, ScExportsHolder, ScImportsHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence.{PrecedenceTypes, SubstitutablePrecedenceHelper}
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ResolveProcessor}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

trait FileDeclarationsHolder
  extends ScDeclarationSequenceHolder
    with ScImportsHolder
    with ScExportsHolder {

  import FileDeclarationsHolder._
  import ScPackageImpl._

  override def processDeclarations(
    processor: PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement,
    place: PsiElement
  ): Boolean = {
    if (isProcessLocalClasses(lastParent)) {
      if (!super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place)) {
        return false
      }
    }

    if (!processDeclarationsFromImports(processor, state, lastParent, place))
      return false

    if (this.context != null)
      return true

    if (place.kindProjectorEnabled) {
      KindProjectorUtil(place.getProject)
        .syntheticDeclarations(place)
        .foreach(processor.execute(_, state))
    }

    if (place.betterMonadicForEnabled) {
      BetterMonadicForSupport(place.getProject)
        .syntheticDeclarations
        .foreach(processor.execute(_, state))
    }

    implicit val scope: GlobalSearchScope = place.resolveScope
    implicit val manager: ScalaPsiManager = ScalaPsiManager.instance(getProject)

    val defaultPackage = manager.noNamePackage
    if (defaultPackage != null) {
      place match {
        case ref: ScReference if ref.refName == "_root_" && ref.qualifier.isEmpty =>
          if (!processor.execute(defaultPackage, state.withRename("_root_")))
            return false
        case _ =>
          if (place != null && PsiTreeUtil.getParentOfType(place, classOf[ScPackaging]) == null) {
            if (!packageProcessDeclarations(defaultPackage)(processor, state, null, place))
              return false

            //NOTE: a lot of unit tests were written with top-level code in `.scala` files (not only definitions, but also expressions).
            //Scala files which contained not only definitions were implicitly treated as scala scripts.
            //It worked fine when we had scala scripts, but after after we dropped scala scripts,
            //a lot of test start failing because some code os not resolved when placed at top level
            //This is a hack not to patch hundreds of tests
            //See SCL-20481
            val processTopLevelDeclarations = this.isInScala3Module || isUnitTestMode
            if (processTopLevelDeclarations) {
              if (!defaultPackage.processTopLevelDeclarations(processor, state, lastParent, place))
                return false
            }
          }
          else if (!BaseProcessor.isImplicitProcessor(processor)) {
            //we will add only packages
            //only packages resolve, no classes from default package
            val name = processor.getHint(NameHint.KEY) match {
              case null => null
              case hint => hint.getName(state)
            }
            if (name == null) {
              def processPackages(packages: Array[_ <: PsiPackage]): Boolean = {
                val iterator = packages.iterator
                while (iterator.hasNext) {
                  val pack = iterator.next()
                  if (!processor.execute(pack, state))
                    return false
                }
                true
              }

              val packages = defaultPackage.getSubPackages(scope)
              if (!processPackages(packages))
                return false

              val migration = PsiMigrationManager.getInstance(getProject).getCurrentMigration
              if (migration != null) {
                val list = migration.getMigrationPackages("")
                val packages = list.toArray(new Array[PsiPackage](list.size)).map(ScPackageImpl(_))
                if (!processPackages(packages))
                  return false
              }
            } else {
              val cachedPackage = manager.getCachedPackageInScope(name)
              val scPackageImpl = cachedPackage.map(ScPackageImpl(_))
              scPackageImpl.foreach { scPackage =>
                if (!processor.execute(scPackage, state))
                  return false
              }
            }
          }
      }
    }

    FileDeclarationsContributor.getAllFor(this).foreach(
      _.processAdditionalDeclarations(processor, this, state, lastParent)
    )

    val checkPredefinedClassesAndPackages = processor match {
      case r: ResolveProcessor => r.checkPredefinedClassesAndPackages()
      case _                   => true
    }

    if (checkPredefinedClassesAndPackages) {
      if (!processImplicitImports(processor, state, place))
        return false
    }

    true
  }

  //noinspection SameParameterValue
  private def isInsidePackage(name: String): Boolean = this match {
    case file: ScalaFile =>
      val packageName = file.getPackageName
      packageName == name || packageName.startsWith(name + ".")
    case _ => false
  }

  private def processImplicitImports(
    processor: PsiScopeProcessor,
    state: ResolveState,
    place: PsiElement
  )(implicit manager: ScalaPsiManager, scope: GlobalSearchScope): Boolean = {
    val precedenceTypes = PrecedenceTypes.forElement(this)
    val importedFqns = precedenceTypes.defaultImportsWithPrecedence

    importedFqns.foreach { case (fqn, precedence) =>
      ProgressManager.checkCanceled()
      if (!shouldNotProcessDefaultImport(fqn)) {

        updateProcessor(processor, precedence) {
          val cachedClasses = manager.getCachedClasses(scope, fqn)
          val scObjectOpt = cachedClasses.findByType[ScObject]
          scObjectOpt.foreach { scObject =>
            if (!processPackageObject(scObject)(processor, state, null, place))
              return false
          }

          val cachedPackage = ScPackageImpl.findPackage(fqn)
          cachedPackage.foreach { pkg =>
            if (!pkg.processDeclarations(processor, state, null, place))
              return false
          }
        }
      }

      /* scala package requires special treatment to process synthetic classes/objects */
      if (fqn == ScalaLowerCase) {
        if (!processScalaPackage(processor, state))
          return false
      }
    }

    true
  }

  protected def shouldNotProcessDefaultImport(fqn: String): Boolean
}

//noinspection TypeAnnotation
object FileDeclarationsHolder {

  //method extracted due to VerifyError in Scala compiler
  private def updateProcessor(processor: PsiScopeProcessor, priority: Int)
                             (body: => Unit): Unit =
    processor match {
      case b: BaseProcessor with SubstitutablePrecedenceHelper => b.runWithPriority(priority)(body)
      case _                                                   => body
    }

  private val isUnitTestMode = ApplicationManager.getApplication.isUnitTestMode

  /**
    * @param _place actual place, can be null, if null => false
    * @return true, if place is out of source content root, or in Scala Worksheet.
    */
  def isProcessLocalClasses(_place: PsiElement): Boolean = {
    val place = _place match {
      case s: ScalaPsiElement => s.getDeepSameElementInContext
      case _ => _place
    }
    if (place == null || place.hasOnlyStub)
      return false

    val scalaFile = place.getContainingFile match {
      case sf: ScalaFile => sf
      case _ =>
        return false
    }

    if (scalaFile.isWorksheetFile)
      true
    else {
      //NOTE: getViewProvider.getVirtualFile will return non-null result even for in-memory files (primarily needed for tests)
      val virtualFile = Option(scalaFile.getOriginalFile.getVirtualFile).getOrElse(scalaFile.getViewProvider.getVirtualFile)
      isExternalFile(scalaFile.getProject, virtualFile)
    }
  }

  /**
   * @return true for scratch file or just some external isolated scala file
   */
  private def isExternalFile(project: Project, vFile: VirtualFile) = {
    val index = ProjectRootManager.getInstance(project).getFileIndex
    val isInSources = index.isInSourceContent(vFile)
    val isInLibraries = index.isInLibraryClasses(vFile)
    !(isInSources || isInLibraries)
  }
}
