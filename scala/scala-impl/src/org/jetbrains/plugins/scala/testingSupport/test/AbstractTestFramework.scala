package org.jetbrains.plugins.scala
package testingSupport.test

import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.lang.Language
import com.intellij.openapi.module.Module
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}
import com.intellij.testIntegration.JavaTestFramework
import javax.swing.Icon
import org.jetbrains.concurrency.{Promise, Promises}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.ElementScope
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.isInheritorDeep
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.sbt.project.modifier.SimpleBuildFileModifier

// TODO: rename to something with `Scala`
abstract class AbstractTestFramework extends JavaTestFramework {

  def testFileTemplateName: String

  def suitePaths: Seq[String]

  override def isTestMethod(element: PsiElement): Boolean = false

  override def getTestMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  override def getTearDownMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  override def getSetUpMethodFileTemplateDescriptor: FileTemplateDescriptor = null

  override def getLibraryPath: String = null

  override def getIcon: Icon = Icons.SCALA_TEST

  override def getLanguage: Language = ScalaLanguage.INSTANCE

  override def findOrCreateSetUpMethod(clazz: PsiClass): PsiMethod = null

  override def findTearDownMethod(clazz: PsiClass): PsiMethod = null

  override def findSetUpMethod(clazz: PsiClass): PsiMethod = null

  override def isTestClass(clazz: PsiClass, canBePotential: Boolean): Boolean = {
    val definition: ScTemplateDefinition = clazz match {
      case PsiClassWrapper(definition)  => definition
      case definition: ScTypeDefinition => definition
      case _                            => return false
    }

    isTestClass(definition)
  }

  protected def isTestClass(definition: ScTemplateDefinition): Boolean = {
    val elementScope = ElementScope(definition.getProject)
    elementScope.getCachedClass(getMarkerClassFQName).isDefined &&
      suitePaths.exists { path =>
        val cachedClass = elementScope.getCachedClass(path)
        cachedClass.exists(isInheritorDeep(definition, _))
      }
  }

  protected def getLibraryDependencies(scalaVersion: Option[String]): Seq[String]

  protected def getLibraryResolvers(scalaVersion: Option[String]): Seq[String]

  protected def getAdditionalBuildCommands(scalaVersion: Option[String]): Seq[String]

  override def setupLibrary(module: Module): Promise[Void] = {
    import org.jetbrains.plugins.scala.project._
    val (libraries, resolvers, options) = module.scalaSdk match {
      case Some(scalaSdk) =>
        val compilerVersion = scalaSdk.compilerVersion

        val dependencies  = getLibraryDependencies(compilerVersion)
        val resolvers     = getLibraryResolvers(compilerVersion)
        val buildCommands = getAdditionalBuildCommands(compilerVersion)
        (dependencies, resolvers, buildCommands)
      case None =>
        throw new RuntimeException("Failed to download test library jars: scala SDK is not specified to module" + module.getName)
    }
    val modifier = new SimpleBuildFileModifier(libraries, resolvers, options)
    modifier.modify(module, needPreviewChanges = true)
    Promises.resolvedPromise()
  }
}