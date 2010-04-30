package org.jetbrains.plugins.scala.lang.psi.impl

import com.intellij.psi.impl.file.PsiPackageImpl
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import com.intellij.psi.impl.PsiManagerEx
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.project.Project
import com.intellij.psi.{JavaPsiFacade, PsiElement, ResolveState, PsiPackage}
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.caches.ScalaCachesManager
import toplevel.synthetic.SyntheticClasses

/**
 * User: Alexander Podkhalyuzin
 * Date: 22.04.2010
 */

class ScPackageImpl(pack: PsiPackage) extends PsiPackageImpl(pack.getManager.asInstanceOf[PsiManagerEx],
        pack.getQualifiedName) with ScPackage {
  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState,
                                   lastParent: PsiElement, place: PsiElement): Boolean = {
    if (!super[PsiPackageImpl].processDeclarations(processor, state, lastParent, place)) return false

    //for Scala
    if (place.getLanguage == ScalaFileType.SCALA_LANGUAGE) {
      // Process synthetic classes for scala._ package
      if (pack.getQualifiedName == "scala") {
        for (synth <- SyntheticClasses.get(getProject).getAll) {
          processor.execute(synth, ResolveState.initial)
        }
      }
      
      val manager = ScalaCachesManager.getInstance(getProject)
      val cache = manager.getNamesCache
      val obj = cache.getPackageObjectByName(getQualifiedName, place.getResolveScope)
      if (obj != null) {
        if (!obj.processDeclarations(processor, state, lastParent, place)) return false
      }
    }
    return true
  }

  override def getParentPackage: PsiPackage = {
    ScPackageImpl(super.getParentPackage)
  }

  override def getSubPackages: Array[PsiPackage] = {
    super.getSubPackages.map(ScPackageImpl(_))
  }

  override def getSubPackages(scope: GlobalSearchScope): Array[PsiPackage] = {
    super.getSubPackages(scope).map(ScPackageImpl(_))
  }
}

object ScPackageImpl {
  def apply(pack: PsiPackage): ScPackageImpl = {
    if (pack == null) null
    else if (pack.isInstanceOf[ScPackageImpl]) pack.asInstanceOf[ScPackageImpl]
    else new ScPackageImpl(pack)
  }

  def findPackage(project: Project, pName: String) = {
    ScPackageImpl(JavaPsiFacade.getInstance(project).findPackage(pName))
  }
}