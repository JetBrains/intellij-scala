package org.jetbrains.plugins.scala
package testingSupport
package specs2

import javax.swing.Icon
import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testIntegration.JavaTestFramework
import icons.Icons
import com.intellij.psi.{PsiElement, PsiMethod, PsiClass, JavaPsiFacade}
import lang.psi.ScalaPsiUtil

class Specs2TestFramework extends JavaTestFramework {
  def getTestMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  def getTearDownMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  def getSetUpMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  def getDefaultSuperClass: String = "org.specs2.mutable.Specification"

  def getLibraryPath: String = ""

  def getIcon: Icon = Icons.SCALA_TEST

  def getName: String = "Specs2"

  def findOrCreateSetUpMethod(clazz: PsiClass): PsiMethod = null

  def findTearDownMethod(clazz: PsiClass): PsiMethod = null

  def findSetUpMethod(clazz: PsiClass): PsiMethod = null

  def isTestClass(clazz: PsiClass, canBePotential: Boolean): Boolean = {
    val parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(clazz, classOf[ScTypeDefinition], false)
    if (parent == null) return false
    val facade = JavaPsiFacade.getInstance(clazz.getProject)
    val suiteClazz: PsiClass = facade.findClass(getMarkerClassFQName, clazz.getResolveScope)
    if (suiteClazz == null) return false
    ScalaPsiUtil.cachedDeepIsInheritor(parent, suiteClazz)
  }

  def getMarkerClassFQName: String = "org.specs2.specification.SpecificationStructure"
}