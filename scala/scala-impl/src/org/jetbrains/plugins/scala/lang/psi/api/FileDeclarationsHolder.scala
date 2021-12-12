package org.jetbrains.plugins.scala
package lang.psi.api

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi._
import com.intellij.psi.impl.migration.PsiMigrationManager
import com.intellij.psi.scope.{NameHint, PsiScopeProcessor}
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.{StubBasedExt, _}
import org.jetbrains.plugins.scala.externalLibraries.bm4.BetterMonadicForSupport
import org.jetbrains.plugins.scala.externalLibraries.kindProjector.KindProjectorUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScPackageLike.processPackageObject
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl._
import org.jetbrains.plugins.scala.lang.psi.{ScDeclarationSequenceHolder, ScExportsHolder, ScImportsHolder}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence.{PrecedenceTypes, SubstitutablePrecedenceHelper}
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ResolveProcessor}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

/**
  * User: Dmitry Naydanov
  * Date: 12/12/12
  */
trait FileDeclarationsHolder
  extends ScDeclarationSequenceHolder
    with ScImportsHolder
    with ScExportsHolder {

  import FileDeclarationsHolder._
  import ScPackageImpl._

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    if (isProcessLocalClasses(lastParent) &&
      !super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place)) return false

    if (!processDeclarationsFromImports(processor, state, lastParent, place)) return false

    if (this.context != null) return true

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

    val defaultPackage = ScPackageImpl.findPackage("")
    place match {
      case ref: ScReference if ref.refName == "_root_" && ref.qualifier.isEmpty =>
        if (defaultPackage != null && !processor.execute(defaultPackage, state.withRename("_root_"))) return false
      case _ =>
        if (place != null && PsiTreeUtil.getParentOfType(place, classOf[ScPackaging]) == null) {
          if (defaultPackage != null &&
            !packageProcessDeclarations(defaultPackage)(processor, state, null, place)) return false
          if (defaultPackage != null &&
            this.isInScala3Module &&
            !defaultPackage.processTopLevelDeclarations(processor, state, place)) return false

        }
        else if (defaultPackage != null && !BaseProcessor.isImplicitProcessor(processor)) {
          //we will add only packages
          //only packages resolve, no classes from default package
          val name = processor.getHint(NameHint.KEY) match {
            case null => null
            case hint => hint.getName(state)
          }
          if (name == null) {
            val packages = defaultPackage.getSubPackages(scope)
            val iterator = packages.iterator
            while (iterator.hasNext) {
              val pack = iterator.next()
              if (!processor.execute(pack, state)) return false
            }
            val migration = PsiMigrationManager.getInstance(getProject).getCurrentMigration
            if (migration != null) {
              val list = migration.getMigrationPackages("")
              val packages = list.toArray(new Array[PsiPackage](list.size)).map(ScPackageImpl(_))
              val iterator = packages.iterator
              while (iterator.hasNext) {
                val pack = iterator.next()
                if (!processor.execute(pack, state)) return false
              }
            }
          } else {
            manager.getCachedPackageInScope(name)
              .map(ScPackageImpl(_))
              .foreach { `package` =>
                if (!processor.execute(`package`, state)) return false
              }
          }
        }
    }

    FileDeclarationsContributor.getAllFor(this).foreach(
      _.processAdditionalDeclarations(processor, this, state)
    )


    val checkPredefinedClassesAndPackages = processor match {
      case r: ResolveProcessor => r.checkPredefinedClassesAndPackages()
      case _                   => true
    }

    if (checkPredefinedClassesAndPackages) {
      if (!processImplicitImports(processor, state, place)) return false
    }

    true
  }

  def processImplicitImports(processor: PsiScopeProcessor,
                             state: ResolveState,
                             place: PsiElement)
                            (implicit manager: ScalaPsiManager,
                             scope: GlobalSearchScope)
  : Boolean = {
    val precedenceTypes = PrecedenceTypes.forElement(this)
    val importedFqns = precedenceTypes.defaultImportsWithPrecedence

    importedFqns.foreach { case (fqn, precedence) =>
      ProgressManager.checkCanceled()
      if (!shouldNotProcessDefaultImport(fqn)) {

        updateProcessor(processor, precedence) {
          manager.getCachedClasses(scope, fqn)
            .findByType[ScObject]
            .foreach { `object` =>
              if (!processPackageObject(`object`)(processor, state, null, place))
                return false
            }

          manager.getCachedPackage(fqn)
            .foreach { `package` =>
              if (!packageProcessDeclarations(`package`)(processor, state, null, place))
                return false
            }
        }
      }

      /* scala package requires special treatment to process synthetic classes/objects */
      if (fqn == ScalaLowerCase &&
        !processScalaPackage(processor, state))
        return false
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

  /**
    * @param _place actual place, can be null, if null => false
    * @return true, if place is out of source content root, or in Scala Worksheet.
    */
  def isProcessLocalClasses(_place: PsiElement): Boolean = {
    val place = _place match {
      case s: ScalaPsiElement => s.getDeepSameElementInContext
      case _ => _place
    }
    if (place == null || place.hasOnlyStub) return false

    place.getContainingFile match {
      case scalaFile: ScalaFile if scalaFile.isWorksheetFile => true
      case scalaFile: ScalaFile =>
        val file = Option(scalaFile.getOriginalFile.getVirtualFile).getOrElse(scalaFile.getViewProvider.getVirtualFile)
        if (file == null) return false

        val index = ProjectRootManager.getInstance(place.getProject).getFileIndex
        val belongsToProject =
          index.isInSourceContent(file) || index.isInLibraryClasses(file)
        !belongsToProject
      case _ => false
    }
  }

}
