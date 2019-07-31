package org.jetbrains.plugins.scala
package lang.psi.api

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.psi._
import com.intellij.psi.impl.migration.PsiMigrationManager
import com.intellij.psi.scope.{NameHint, PsiScopeProcessor}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.lang.psi.{ScDeclarationSequenceHolder, ScImportsHolder}
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence.{PrecedenceTypes, SubstitutablePrecedenceHelper}
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ResolveProcessor}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.util.{BetterMonadicForSupport, KindProjectorUtil}
import org.jetbrains.plugins.scala.worksheet.FileDeclarationsContributor

import scala.collection.mutable

/**
  * User: Dmitry Naydanov
  * Date: 12/12/12
  */
trait FileDeclarationsHolder extends ScDeclarationSequenceHolder with ScImportsHolder {

  import FileDeclarationsHolder._

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    if (!super[ScImportsHolder].processDeclarations(processor, state, lastParent, place)) return false

    if (this.context != null) return true

    if (place.kindProjectorPluginEnabled) {
      KindProjectorUtil(place.getProject)
        .syntheticDeclarations
        .foreach(processor.execute(_, state))
    }

    if (place.betterMonadicForEnabled) {
      BetterMonadicForSupport(place.getProject)
        .syntheticDeclarations
        .foreach(processor.execute(_, state))
    }

    val scope = place.resolveScope

    val manager = ScalaPsiManager.instance(getProject)

    place match {
      case ref: ScReference if ref.refName == "_root_" && ref.qualifier.isEmpty =>
        val top = ScPackageImpl(manager.getCachedPackage("").orNull)
        if (top != null && !processor.execute(top, state.withRename("_root_"))) return false
      case _ =>
        val defaultPackage = ScPackageImpl(manager.getCachedPackage("").orNull)
        if (place != null && PsiTreeUtil.getParentOfType(place, classOf[ScPackaging]) == null) {
          if (defaultPackage != null &&
            !ResolveUtils.packageProcessDeclarations(defaultPackage, processor, state, null, place)) return false
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
            val aPackage: PsiPackage = ScPackageImpl(manager.getCachedPackageInScope(name, scope).orNull)
            if (aPackage != null && !processor.execute(aPackage, state)) return false
          }
        }
    }

    FileDeclarationsContributor.getAllFor(this).foreach(
      _.processAdditionalDeclarations(processor, this, state)
    )

    val checkPredefinedClassesAndPackages = processor match {
      case r: ResolveProcessor => r.checkPredefinedClassesAndPackages()
      case _ => true
    }

    val checkWildcardImports = processor match {
      case r: ResolveProcessor => r.checkWildcardImports()
      case _ => true
    }

    def checkObjects(priority: Int,
                     objects: Set[String] = Set.empty): Boolean = {
      val attachedQualifiers = mutable.HashSet.empty[String]

      val implObjects = mutable.ArrayBuffer.empty[PsiClass]
      for (obj <- objects) {
        implObjects ++= ScalaPsiManager.instance(getProject).getCachedClasses(scope, obj)
      }

      val implObjIter = implObjects.iterator

      updateProcessor(processor, priority) {
        while (implObjIter.hasNext) {
          val clazz = implObjIter.next()
          if (!attachedQualifiers.contains(clazz.qualifiedName)) {
            attachedQualifiers += clazz.qualifiedName
            ProgressManager.checkCanceled()

            clazz match {
              case td: ScTypeDefinition if !isScalaPredefinedClass =>
                val newState = state.withFromType(td.`type`().toOption)
                if (!clazz.processDeclarations(processor, newState, null, place)) return false
              case _ =>
            }
          }
        }
      }
      true
    }

    def checkPackages(): Boolean = {
      val iterator = DefaultImplicitlyImportedPackages.iterator
      while (iterator.hasNext) {
        ProgressManager.checkCanceled()

        manager.getCachedPackage(iterator.next()) match {
          case Some(pack) if !ResolveUtils.packageProcessDeclarations(pack, processor, state, null, place) => return false
          case _ =>
        }
      }

      true
    }

    if (checkWildcardImports &&
      !checkObjects(PrecedenceTypes.WILDCARD_IMPORT)) return false

    if (checkPredefinedClassesAndPackages) {
      if (!checkObjects(PrecedenceTypes.SCALA_PREDEF, DefaultImplicitlyImportedObjects)) return false

      val scalaPack = ScPackageImpl.findPackage(getProject, "scala")
      val namesSet =
        if (scalaPack != null) ScalaShortNamesCacheManager.getInstance(getProject).getClassNames(scalaPack, scope)
        else Set.empty[String]

      def alreadyContains(className: String) = namesSet.contains(className)

      val classes = SyntheticClasses.get(getProject)
      val synthIterator = classes.getAll.iterator
      while (synthIterator.hasNext) {
        val synth = synthIterator.next()
        ProgressManager.checkCanceled()
        if (!alreadyContains(synth.getName) && !processor.execute(synth, state)) return false
      }

      val synthObjectsIterator = classes.syntheticObjects.valuesIterator
      while (synthObjectsIterator.hasNext) {
        val synth = synthObjectsIterator.next()
        ProgressManager.checkCanceled()
        if (!alreadyContains(synth.name) && !processor.execute(synth, state)) return false
      }

      if (!checkPackages()) return false
    }

    if (isProcessLocalClasses(lastParent) && !super[ScDeclarationSequenceHolder].processDeclarations(processor, state, lastParent, place)) return false

    true
  }

  protected def isScalaPredefinedClass: Boolean
}

//noinspection TypeAnnotation
object FileDeclarationsHolder {

  private[psi] val DefaultImplicitlyImportedPackages = Set("scala", "java.lang")
  private[psi] val DefaultImplicitlyImportedObjects = Set("scala.Predef", "scala" /* package object*/)

  //method extracted due to VerifyError in Scala compiler
  private def updateProcessor(processor: PsiScopeProcessor, priority: Int)
                             (body: => Unit): Unit =
    processor match {
      case b: BaseProcessor with SubstitutablePrecedenceHelper => b.runWithPriority(priority)(body)
      case _ => body
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
    if (place == null) return false

    place.getContainingFile match {
      case scalaFile: ScalaFile if scalaFile.isWorksheetFile => true
      case scalaFile: ScalaFile =>
        val file = scalaFile.getVirtualFile
        if (file == null) return false

        val index = ProjectRootManager.getInstance(place.getProject).getFileIndex
        !(index.isInSourceContent(file) ||
          index.isInLibraryClasses(file) ||
          index.isInLibrarySource(file))
      case _ => false
    }
  }

}
