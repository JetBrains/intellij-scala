package org.jetbrains.plugins.scala
package testingSupport
package scalaTest

import javax.swing.Icon
import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import lang.psi.api.toplevel.typedef.ScTypeDefinition
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testIntegration.JavaTestFramework
import com.intellij.psi.{PsiMethod, PsiClass, JavaPsiFacade}
import icons.Icons

class ScalatestTestFramework extends JavaTestFramework {
  def getTestMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  def getTearDownMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  def getSetUpMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  def getDefaultSuperClass: String = "org.scalatest.FunSuite"

  def getLibraryPath: String = ""

  def getIcon: Icon = Icons.SCALA_TEST

  def getName: String = "ScalaTest"

  def findOrCreateSetUpMethod(clazz: PsiClass): PsiMethod = null

  def findTearDownMethod(clazz: PsiClass): PsiMethod = null

  def findSetUpMethod(clazz: PsiClass): PsiMethod = null

  def isTestClass(clazz: PsiClass, canBePotential: Boolean): Boolean = {
    val parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(clazz, classOf[ScTypeDefinition], false)
    if (parent == null) return false
    val facade = JavaPsiFacade.getInstance(clazz.getProject)
    val suiteClazz: PsiClass = facade.findClass(getMarkerClassFQName, clazz.getResolveScope)
    if (suiteClazz == null) return false
    parent.isInheritor(suiteClazz, true)
  }

  def getMarkerClassFQName: String = "org.scalatest.Suite"
}