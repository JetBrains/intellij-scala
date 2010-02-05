package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package synthetic

import collection.mutable.HashSet
import caches.ScalaCachesManager
import java.util.ArrayList
import api.toplevel.packaging.ScPackageContainer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndexKey
import resolve.BaseProcessor
import resolve.ResolveTargets._
import stubs.index.ScalaIndexKeys
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.util.IncorrectOperationException

/**
 * @author ilyas
 */

abstract class ScSyntheticPackage(name: String, manager: PsiManager)
        extends LightElement(manager, ScalaFileType.SCALA_LANGUAGE) with PsiPackage {

  def handleQualifiedNameChange(newQualifiedName: String) {
  }
  def getDirectories = PsiDirectory.EMPTY_ARRAY
  def checkSetName(s: String) {
    throw new IncorrectOperationException("cannot set name: nonphysical element")
  }
  def getText = ""
  override def toString = "Scala Synthetic Package " + getQualifiedName
  def getDirectories(scope: GlobalSearchScope) = PsiDirectory.EMPTY_ARRAY
  def getModifierList = null
  def hasModifierProperty(s: String) = false
  def getAnnotationList = null
  def getName = name
  def setName(newName: String) = throw new IncorrectOperationException("cannot set name: nonphysical element")
  def copy = throw new IncorrectOperationException("cannot copy: nonphysical element")
  def accept(v: PsiElementVisitor) = throw new IncorrectOperationException("should not call")
  override def getContainingFile = SyntheticClasses.get(manager.getProject).file
  def occursInPackagePrefixes = VirtualFile.EMPTY_ARRAY

  private def getPackageObject = {
    val manager = ScalaCachesManager.getInstance(getProject)
    val cache = manager.getNamesCache
    cache.getPackageObjectByName(getQualifiedName, GlobalSearchScope.allScope(getProject))
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
     processor match {
      case bp: BaseProcessor => {
        if (bp.kinds.contains(PACKAGE)) {
          for (subp <- getSubPackages) {
            if (!processor.execute(subp, state)) return false
          }
        }
        if (bp.kinds.contains(CLASS) || bp.kinds.contains(OBJECT) || bp.kinds.contains(METHOD)) {
          for (clazz <- getClasses) {
            if (!processor.execute(clazz, state)) return false
          }
        }
        true
      }
      case _ => true
    }
  }
}


object ScSyntheticPackage {
  def get(fqn: String, project: Project): ScSyntheticPackage = {
    val i = fqn.lastIndexOf(".")
    val name = if (i < 0) fqn else fqn.substring(i + 1)

    import com.intellij.psi.stubs.StubIndex

    val packages = collection.immutable.Seq(StubIndex.getInstance().get(
      ScalaIndexKeys.PACKAGE_FQN_KEY.asInstanceOf[StubIndexKey[Any, ScPackageContainer]],
      fqn.hashCode(), project, GlobalSearchScope.allScope(project)).toArray(Array[ScPackageContainer]()).toSeq : _*)

    if (packages.isEmpty) null else {
      val pkgs = packages.filter(pc => {
          pc.fqn.startsWith(fqn) && fqn.startsWith(pc.prefix)
      })

      if (pkgs.isEmpty) null else {
        val pname = if (i < 0) "" else fqn.substring(0, i)
        new ScSyntheticPackage(name, PsiManager.getInstance(project)) {
          def getQualifiedName = fqn

          def getClasses = Array(pkgs.flatMap(p => if (p.fqn.length == fqn.length) p.typeDefs else Seq.empty): _*)

          def getClasses(scope: GlobalSearchScope) =
            getClasses.filter{clazz => scope.contains(clazz.getContainingFile.getVirtualFile)}

          def getParentPackage = JavaPsiFacade.getInstance(project).findPackage(pname)

          def getSubPackages = {
            val buff = new HashSet[PsiPackage]
            pkgs.foreach{
              p =>
              def addPackage(tail : String) {
                val p = JavaPsiFacade.getInstance(project).findPackage(fqn + "." + tail)
                if (p != null) buff += p
              }

              val fqn1 = p.fqn
              val tail = if (fqn1.length > fqn.length) fqn1.substring(fqn.length + 1) else ""
              if (tail.length == 0)
                p.packagings.foreach {
                  pack => {
                    val own = pack.ownNamePart
                    val i = own.indexOf(".")
                    addPackage(if (i > 0) own.substring(0, i) else own)
                  }
                }
              else {
                val i = tail.indexOf(".")
                val next = if (i > 0) tail.substring(0, i) else tail
                addPackage(next)
              }
            }
            buff.toArray
          }
          def getSubPackages(scope: GlobalSearchScope) = getSubPackages


          def getContainer: PsiQualifiedNamedElement = null
        }
      }
    }
  }
}

class SyntheticPackageCreator(project: Project) {
  def getPackage(fqn: String) = ScSyntheticPackage.get(fqn, project)
}