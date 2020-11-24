package org.jetbrains.plugins.scala
package lang
package psi
package impl

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.scope.{NameHint, PsiScopeProcessor}
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScPackageLike}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.SyntheticClasses
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ResolveProcessor}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveState}
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.04.2010
 */
final class ScPackageImpl private(val pack: PsiPackage) extends PsiPackageImpl(
  pack.getManager.asInstanceOf[PsiManagerEx],
  pack.getQualifiedName
) with ScPackage {

  import ScPackageImpl._

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    val isInScalaContext = place.getLanguage.isKindOf(ScalaLanguage.INSTANCE)
    implicit val manager: ScalaPsiManager = ScalaPsiManager.instance(getProject)

    def processPackageObject(`object`: ScObject): Boolean =
      ScPackageLike.processPackageObject(`object`)(processor, state, lastParent, place)

    getQualifiedName match {
      case ScalaLowerCase if isInScalaContext =>
        implicit val scope: GlobalSearchScope = findScope(processor, place)

        if (!BaseProcessor.isImplicitProcessor(processor)) {
          processScalaPackage(processor)
        }

        manager.getCachedClasses(scope, ScalaLowerCase)
          .findByType[ScObject]
          .forall(processPackageObject)
      case _ if !packageProcessDeclarations(pack)(processor, state, lastParent, place) => false
      case _ if isInScalaContext =>
        val scope = findScope(processor, place)
        if (!findPackageObject(scope).forall(processPackageObject)) return false
        if (!processTopLevelDeclarations(processor, state, place)) return false
        true
      case _ =>
        true
    }
  }

  @CachedInUserData(this, ScalaPsiManager.instance(getProject).TopLevelModificationTracker)
  override def findPackageObject(scope: GlobalSearchScope): Option[ScObject] =
    ScalaShortNamesCacheManager.getInstance(getProject).findPackageObjectByName(getQualifiedName, scope)

  override def fqn: String = getQualifiedName

  override def getParentPackage: PsiPackageImpl =
    ScalaPsiUtil.parentPackage(getQualifiedName, getProject)
      .orNull

  override def getSubPackages: Array[PsiPackage] =
    super.getSubPackages
      .map(ScPackageImpl(_))

  override def getSubPackages(scope: GlobalSearchScope): Array[PsiPackage] =
    super.getSubPackages(scope)
      .map(ScPackageImpl(_))

  override def isValid: Boolean = true

  override def parentScalaPackage: Option[ScPackageLike] = getParentPackage match {
    case p: ScPackageLike => Some(p)
    case _ => None
  }
}

object ScPackageImpl {

  def apply(psiPackage: PsiPackage): ScPackageImpl = psiPackage match {
    case impl: ScPackageImpl => impl
    case null => null
    case _ => new ScPackageImpl(psiPackage)
  }

  def findPackage(project: Project, packageName: String): ScPackageImpl =
    findPackage(packageName)(ScalaPsiManager.instance(project))

  def findPackage(packageName: String)
                 (implicit manager: ScalaPsiManager): ScPackageImpl =
    manager.getCachedPackage(packageName)
      .map(apply)
      .orNull

  /**
   * Process synthetic classes for scala._ package
   */
  def processScalaPackage(processor: PsiScopeProcessor,
                          state: ResolveState = ScalaResolveState.empty)
                         (implicit manager: ScalaPsiManager,
                          scope: GlobalSearchScope): Boolean = {
    val namesSet = manager.getScalaPackageClassNames

    val syntheticClasses = SyntheticClasses.get(manager.project)
    for {
      syntheticElement <- syntheticClasses.getAll ++
        syntheticClasses.syntheticObjects.valuesIterator
      // Assume that is the scala package contained a class with the same names as the synthetic object, then it must also contain the object.

      // Does the "scala" package already contain a class named `className`?
      // SCL-2913
      if !namesSet(syntheticElement.name)
    } {
      ProgressManager.checkCanceled()
      if (!processor.execute(syntheticElement, state)) return false
    }

    true
  }

  private[psi] def packageProcessDeclarations(`package`: PsiPackage)
                                             (processor: PsiScopeProcessor,
                                              state: ResolveState,
                                              lastParent: PsiElement,
                                              place: PsiElement)
                                             (implicit manager: ScalaPsiManager): Boolean = processor match {
    case b: BaseProcessor if b.isImplicitProcessor =>
      val pkgFqn = `package`.getQualifiedName
      val scope  = place.resolveScope

      val topLevelImplicits =
        manager.getPackageImplicitObjects(pkgFqn, scope).iterator ++
          manager.getTopLevelImplicitClassesByPackage(pkgFqn, scope).iterator
                 .flatMap(_.getSyntheticImplicitMethod)

      while (topLevelImplicits.hasNext) {
        val obj = topLevelImplicits.next()
        if (!processor.execute(obj, state)) return false
      }
      true
    case base: BaseProcessor =>
      val nameHint = base.getHint(NameHint.KEY)
      val name = if (nameHint == null) "" else nameHint.getName(state)
      if (name != null && name != "" && base.getClassKind) {
        try {
          base.setClassKind(classKind = false)

          if (base.getClassKindInner) {
            val qName = `package`.getQualifiedName

            val calcForName = {
              val fqn = if (qName.nonEmpty) qName + "." + name else name

              val scope = base match {
                case r: ResolveProcessor => r.getResolveScope
                case _                   => place.resolveScope
              }

              val classes = manager.getCachedClasses(scope, fqn).iterator
              var stop = false
              while (classes.hasNext && !stop) {
                val clazz = classes.next()
                clazz match {
                  case cls: ScClass if cls.isTopLevel =>
                    cls.getSyntheticImplicitMethod.foreach {
                      synMethod => if (!processor.execute(synMethod, state)) return false
                    }
                  case _ => ()
                  case e: ScEnum    => e.syntheticClass.foreach(processor.execute(_, state))
                  case _            => ()
                }

                stop = clazz.containingClass == null && !processor.execute(clazz, state)
              }
              !stop
            }
            if (!calcForName) return false
          }

          //process subpackages
          if (base.kinds.contains(ResolveTargets.PACKAGE)) {
            val psiPack = `package` match {
              case s: ScPackageImpl => s.pack
              case _ => `package`
            }
            val qName: String = psiPack.getQualifiedName
            val subpackageQName: String = if (qName.isEmpty) name else qName + "." + name
            manager.getCachedPackageInScope(subpackageQName)(place.getResolveScope)
              .foreach { `package` =>
                if (!processor.execute(`package`, state)) return false
              }
            true
          } else true
        } finally {
          base.setClassKind(classKind = true)
        }
      } else {
        try {
          if (base.getClassKindInner) {
            base.setClassKind(classKind = false)
            val scope = base match {
              case r: ResolveProcessor => r.getResolveScope
              case _ => place.resolveScope
            }
            val iterator = manager.getClasses(`package`)(scope).iterator
            while (iterator.hasNext) {
              val clazz = iterator.next()
              if (clazz.containingClass == null && !processor.execute(clazz, state)) return false
            }
          }

          if (base.kinds.contains(ResolveTargets.PACKAGE)) {
            //process subpackages
            `package` match {
              case s: ScPackageImpl =>
                s.pack.processDeclarations(processor, state, lastParent, place)
              case _ =>
                `package`.processDeclarations(processor, state, lastParent, place)
            }
          } else true
        } finally {
          base.setClassKind(classKind = true)
        }
      }
    case _ => `package`.processDeclarations(processor, state, lastParent, place)
  }

  private def findScope(processor: PsiScopeProcessor,
                        place: PsiElement) = processor match {
    case processor: ResolveProcessor => processor.getResolveScope
    case _ => place.resolveScope
  }
}