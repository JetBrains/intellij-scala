package org.jetbrains.plugins.scala
package testingSupport.test

import javax.swing.Icon

import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.lang.Language
import com.intellij.openapi.module.Module
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}
import com.intellij.testIntegration.JavaTestFramework
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.sbt.project.modifier.SimpleBuildFileModifier

/**
 * @author Ksenia.Sautina
 * @since 5/18/12
 */

abstract class AbstractTestFramework extends JavaTestFramework {
  def isTestMethod(element: PsiElement): Boolean = false

  def getTestMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  def getTearDownMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  def getSetUpMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  override def getLibraryPath: String = null

  def getIcon: Icon = Icons.SCALA_TEST

  def findOrCreateSetUpMethod(clazz: PsiClass): PsiMethod = null

  def findTearDownMethod(clazz: PsiClass): PsiMethod = null

  def findSetUpMethod(clazz: PsiClass): PsiMethod = null

  def isTestClass(clazz: PsiClass, canBePotential: Boolean): Boolean = {
    val parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(clazz match {
      case wrapper: PsiClassWrapper => wrapper.definition
      case _ => clazz
    }, classOf[ScTypeDefinition], false)
    if (parent == null) return false
    val project = clazz.getProject


    ScalaPsiManager.instance(project)
      .getCachedClass(getMarkerClassFQName, GlobalSearchScope.allScope(project), ScalaPsiManager.ClassCategory.TYPE)
      .exists {
        ScalaPsiUtil.cachedDeepIsInheritor(parent, _)
      }
  }

  override def getLanguage: Language = ScalaFileType.SCALA_LANGUAGE

  def generateObjectTests = false

  protected def getLibraryDependencies(scalaVersion: Option[String]): Seq[String]

  protected def getLibraryResolvers(scalaVersion: Option[String]): Seq[String]

  protected def getAdditionalBuildCommands(scalaVersion: Option[String]): Seq[String]

  override def setupLibrary(module: Module) {
    import org.jetbrains.plugins.scala.project._
    val (libraries, resolvers, options) = module.scalaSdk match {
      case Some(scalaSdk) =>
        val compilerVersion = scalaSdk.compilerVersion
        (getLibraryDependencies(compilerVersion), getLibraryResolvers(compilerVersion), getAdditionalBuildCommands(compilerVersion))
      case None => throw new RuntimeException("Failed to download test library jars: scala SDK is not specified to module" + module.getName)
    }
    val modifier = new SimpleBuildFileModifier(libraries, resolvers, options)
    modifier.modify(module, needPreviewChanges = true)
  }
}