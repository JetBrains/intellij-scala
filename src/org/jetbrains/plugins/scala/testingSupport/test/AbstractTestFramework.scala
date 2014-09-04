package org.jetbrains.plugins.scala
package testingSupport.test

import javax.swing.Icon

import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.lang.Language
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}
import com.intellij.testIntegration.JavaTestFramework
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

/**
 * @author Ksenia.Sautina
 * @since 5/18/12
 */

abstract class AbstractTestFramework extends JavaTestFramework {
  def isTestMethod(element: PsiElement): Boolean = false

  def getTestMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  def getTearDownMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  def getSetUpMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  def getLibraryPath: String = ""

  def getIcon: Icon = Icons.SCALA_TEST

  def findOrCreateSetUpMethod(clazz: PsiClass): PsiMethod = null

  def findTearDownMethod(clazz: PsiClass): PsiMethod = null

  def findSetUpMethod(clazz: PsiClass): PsiMethod = null

  def isTestClass(clazz: PsiClass, canBePotential: Boolean): Boolean = {
    val parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(clazz, classOf[ScTypeDefinition], false)
    if (parent == null) return false
    val project = clazz.getProject
    val suiteClazz: PsiClass = ScalaPsiManager.instance(project).getCachedClass(getMarkerClassFQName,
      GlobalSearchScope.allScope(project), ScalaPsiManager.ClassCategory.TYPE)
//    val suiteClazz: PsiClass = ScalaPsiManager.instance(parent.getProject).
//            getCachedClass(getMarkerClassFQName, clazz.getResolveScope, ScalaPsiManager.ClassCategory.TYPE)
    if (suiteClazz == null) return false
    ScalaPsiUtil.cachedDeepIsInheritor(parent, suiteClazz)
  }

  override def getLanguage: Language = ScalaFileType.SCALA_LANGUAGE
}