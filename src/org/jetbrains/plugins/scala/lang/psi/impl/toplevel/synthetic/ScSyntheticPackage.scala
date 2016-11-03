package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package synthetic

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets._
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

import scala.collection.mutable.HashSet

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
  override def getText = ""
  override def toString: String = "Scala Synthetic Package " + getQualifiedName
  def getDirectories(scope: GlobalSearchScope) = PsiDirectory.EMPTY_ARRAY
  def getModifierList: PsiModifierList = ScalaPsiUtil.getEmptyModifierList(getManager)
  def hasModifierProperty(s: String) = false
  def getAnnotationList = null
  override def getName: String = name
  def setName(newName: String) = throw new IncorrectOperationException("cannot set name: nonphysical element")
  override def copy = throw new IncorrectOperationException("cannot copy: nonphysical element")
  override def getContainingFile: PsiFile = SyntheticClasses.get(manager.getProject).file
  def occursInPackagePrefixes = VirtualFile.EMPTY_ARRAY

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
     processor match {
      case bp: BaseProcessor =>
        if (bp.kinds.contains(PACKAGE)) {
          val subPackages = if (lastParent != null) getSubPackages(lastParent.getResolveScope) else getSubPackages
          for (subp <- subPackages) {
            if (!processor.execute(subp, state)) return false
          }
        }
        if (bp.kinds.contains(CLASS) || bp.kinds.contains(OBJECT) || bp.kinds.contains(METHOD)) {
          for (clazz <- getClasses) {
            if (!processor.execute(clazz, state)) return false
          }
        }
        true
      case _ => true
    }
  }
}


object ScSyntheticPackage {
  def get(fqn: String, project: Project): ScSyntheticPackage = {
    val i = fqn.lastIndexOf(".")
    val name = if (i < 0) fqn else fqn.substring(i + 1)

    val cleanName = ScalaNamesUtil.cleanFqn(fqn)

    import com.intellij.psi.stubs.StubIndex

    import scala.collection.JavaConversions._
    val packages = StubIndex.getElements(
      ScalaIndexKeys.PACKAGE_FQN_KEY.asInstanceOf[StubIndexKey[Any, ScPackaging]],
      cleanName.hashCode(), project, GlobalSearchScope.allScope(project), classOf[ScPackaging]).toSeq

    if (packages.isEmpty) {
      StubIndex.getElements(
        ScalaIndexKeys.PACKAGE_OBJECT_KEY.asInstanceOf[StubIndexKey[Any, PsiClass]],
        cleanName.hashCode(), project, GlobalSearchScope.allScope(project), classOf[PsiClass]).toSeq.
        find(pc => {
          ScalaNamesUtil.equivalentFqn(pc.qualifiedName, fqn)
      }) match {
        case Some(_) =>
          val pname = if (i < 0) "" else fqn.substring(0, i)
          new ScSyntheticPackage(name, PsiManager.getInstance(project)) {
            override def getFiles(globalSearchScope: GlobalSearchScope): Array[PsiFile] = Array.empty //todo: ?
            def containsClassNamed(name: String): Boolean = false
            def getQualifiedName: String = fqn
            def getClasses: Array[PsiClass] = Array.empty
            def getClasses(scope: GlobalSearchScope): Array[PsiClass] = Array.empty
            def getParentPackage: ScPackageImpl = ScPackageImpl.findPackage(project, pname)
            def getSubPackages: Array[PsiPackage] = Array.empty
            def getSubPackages(scope: GlobalSearchScope): Array[PsiPackage] = Array.empty
            def getContainer: PsiQualifiedNamedElement = null
            def findClassByShortName(name: String, scope: GlobalSearchScope): Array[PsiClass] = Array.empty
          }
        case None => null
      }
    } else {
      val pkgs = packages.filter(pc => {
        ScalaNamesUtil.cleanFqn(pc.fullPackageName).startsWith(cleanName) && cleanName.startsWith(ScalaNamesUtil.cleanFqn(pc.parentPackageName))
      })

      if (pkgs.isEmpty) null else {
        val pname = if (i < 0) "" else fqn.substring(0, i)
        new ScSyntheticPackage(name, PsiManager.getInstance(project)) {
          override def getFiles(globalSearchScope: GlobalSearchScope): Array[PsiFile] = Array.empty //todo: ?

          def findClassByShortName(name: String, scope: GlobalSearchScope): Array[PsiClass] = {
            getClasses.filter(n => ScalaNamesUtil.equivalentFqn(n.name, name))
          }

          def containsClassNamed(name: String): Boolean = {
            getClasses.exists(n => ScalaNamesUtil.equivalentFqn(n.name, name))
          }

          def getQualifiedName: String = fqn

          def getClasses: Array[PsiClass] = {
            Array(pkgs.flatMap(p =>
              if (ScalaNamesUtil.cleanFqn(p.fullPackageName).length == cleanName.length)
                p.typeDefs.flatMap {
                  case td@(c: ScTypeDefinition) if c.fakeCompanionModule.isDefined =>
                    Seq(td, c.fakeCompanionModule.get)
                  case td => Seq(td)
                }
              else Seq.empty): _*)
          }

          def getClasses(scope: GlobalSearchScope): Array[PsiClass] =
            getClasses.filter{clazz => {
              val file = clazz.getContainingFile.getVirtualFile
              file != null && scope.contains(file)
            }}

          def getParentPackage: ScPackageImpl = ScPackageImpl.findPackage(project, pname)

          def getSubPackages: Array[PsiPackage] = {
            val buff = new HashSet[PsiPackage]
            pkgs.foreach{
              p =>
              def addPackage(tail : String) {
                val p = ScPackageImpl.findPackage(project, fqn + "." + tail)
                if (p != null) buff += p
              }

                val fqn1 = p.fullPackageName
              val tail = if (fqn1.length > fqn.length) fqn1.substring(fqn.length + 1) else ""
              if (tail.length == 0) {
                p.packagings.foreach {
                  pack => {
                    val own = pack.packageName
                    val i = own.indexOf(".")
                    addPackage(if (i > 0) own.substring(0, i) else own)
                  }
                }
                p.typeDefs.foreach {
                  case o: ScObject if o.isPackageObject && o.getName != "`package`" =>
                    addPackage(o.name)
                  case _ =>
                }
              } else {
                val i = tail.indexOf(".")
                val next = if (i > 0) tail.substring(0, i) else tail
                addPackage(next)
              }
            }
            buff.toArray
          }
          def getSubPackages(scope: GlobalSearchScope): Array[PsiPackage] = getSubPackages


          def getContainer: PsiQualifiedNamedElement = null
        }
      }
    }
  }
}

class SyntheticPackageCreator(project: Project) {
  def getPackage(fqn: String): ScSyntheticPackage = ScSyntheticPackage.get(fqn, project)
}
