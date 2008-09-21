package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic

import java.util.ArrayList
import api.toplevel.packaging.ScPackageContainer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.{PsiDirectory, PsiManager, PsiElementVisitor, PsiPackage, PsiClass, JavaPsiFacade}
import com.intellij.util.IncorrectOperationException
import stubs.index.ScalaIndexKeys

/**
 * @author ilyas
 */

abstract class ScSyntheticPackage(name: String, manager: PsiManager)
extends LightElement(manager, ScalaFileType.SCALA_LANGUAGE) with PsiPackage {
  def handleQualifiedNameChange(newQualifiedName: String) {
  }
  def getDirectories = PsiDirectory.EMPTY_ARRAY
  def checkSetName(s: String) {
  }
  def getText = ""
  override def toString = "Scala Synthetic Package " + getQualifiedName
  def getDirectories(scope: GlobalSearchScope) = PsiDirectory.EMPTY_ARRAY
  def getModifierList = null
  def hasModifierProperty(s: String) = false
  def getAnnotationList = null
  def getName = name
  def setName(newName: String) = throw new IncorrectOperationException("nonphysical element")
  def copy = throw new IncorrectOperationException("nonphysical element")
  def accept(v: PsiElementVisitor) = throw new IncorrectOperationException("should not call")
  override def getContainingFile = SyntheticClasses.get(manager.getProject).file
  def occursInPackagePrefixes = VirtualFile.EMPTY_ARRAY
}


object ScSyntheticPackage {
  def get(fqn: String, project: Project): ScSyntheticPackage = {
    val i = fqn.lastIndexOf(".")
    val name = if (i < 0) fqn else fqn.substring(i + 1)

    import com.intellij.psi.stubs.StubIndex

    val packages = StubIndex.getInstance().get(
      ScalaIndexKeys.PACKAGE_FQN_KEY.asInstanceOf[StubIndexKey[Any, ScPackageContainer]],
      fqn.hashCode(), project, GlobalSearchScope.allScope(project))

    if (packages.isEmpty) null else {
      import _root_.scala.collection.jcl.Conversions._ //to provide for magic java to scala collections conversions
      val pkgs = new ArrayList[ScPackageContainer](packages).filter(pc => pc.fqn.startsWith(fqn) && fqn.startsWith(pc.prefix))
      if (pkgs.isEmpty) null else {
        val pname = if (i < 1) "" else fqn.substring(0, i - 1)
        new ScSyntheticPackage(name, PsiManager.getInstance(project)) {
          def getQualifiedName = fqn
          def getClasses = {
            Array(pkgs.flatMap(p => p.typeDefs): _*)
          }

          //todo implement them!
          def getClasses(scope: GlobalSearchScope) = {
            PsiClass.EMPTY_ARRAY
          }
          def getParentPackage = JavaPsiFacade.getInstance(project).findPackage(pname)
          def getSubPackages = Array[PsiPackage]()
          def getSubPackages(scope: GlobalSearchScope) = Array[PsiPackage]()
        }
      }
    }
  }
}

class SyntheticPackageCreator(project: Project) {
  def getPackage(fqn: String) = ScSyntheticPackage.get(fqn, project)
}