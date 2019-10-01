package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.lang.Language
import com.intellij.openapi.module.Module
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}
import com.intellij.testIntegration.JavaTestFramework
import javax.swing.Icon
import org.jetbrains.concurrency.{Promise, Promises}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.isInheritorDeep
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.sbt.project.modifier.SimpleBuildFileModifier

abstract class AbstractTestFramework extends JavaTestFramework {
  override def isTestMethod(element: PsiElement): Boolean = false

  override def getTestMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  override def getTearDownMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  override def getSetUpMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  override def getLibraryPath: String = null

  override def getIcon: Icon = Icons.SCALA_TEST

  override def findOrCreateSetUpMethod(clazz: PsiClass): PsiMethod = null

  override def findTearDownMethod(clazz: PsiClass): PsiMethod = null

  override def findSetUpMethod(clazz: PsiClass): PsiMethod = null

  override def isTestClass(clazz: PsiClass, canBePotential: Boolean): Boolean = {
    val newClazz = clazz match {
      case PsiClassWrapper(definition) => definition
      case _ => clazz
    }

    val parent = newClazz.parentOfType(classOf[ScTypeDefinition], strict = false)
      .getOrElse(return false)

    val elementScope = ElementScope(clazz.getProject)

    elementScope.getCachedClass(getMarkerClassFQName).isDefined &&
      getSuitePaths.exists { path =>
        val cachedClass = elementScope.getCachedClass(path)
        cachedClass.exists(isInheritorDeep(parent, _))
      }
  }

  override def getLanguage: Language = ScalaLanguage.INSTANCE

  def getTestFileTemplateName: String

  protected def getLibraryDependencies(scalaVersion: Option[String]): Seq[String]

  protected def getLibraryResolvers(scalaVersion: Option[String]): Seq[String]

  protected def getAdditionalBuildCommands(scalaVersion: Option[String]): Seq[String]

  override def setupLibrary(module: Module): Promise[Void] = {
    import org.jetbrains.plugins.scala.project._
    val (libraries, resolvers, options) = module.scalaSdk match {
      case Some(scalaSdk) =>
        val compilerVersion = scalaSdk.compilerVersion
        (getLibraryDependencies(compilerVersion), getLibraryResolvers(compilerVersion), getAdditionalBuildCommands(compilerVersion))
      case None =>
        throw new RuntimeException("Failed to download test library jars: scala SDK is not specified to module" + module.getName)
    }
    val modifier = new SimpleBuildFileModifier(libraries, resolvers, options)
    modifier.modify(module, needPreviewChanges = true)
    Promises.resolvedPromise()
  }

  def getSuitePaths: Seq[String]
}