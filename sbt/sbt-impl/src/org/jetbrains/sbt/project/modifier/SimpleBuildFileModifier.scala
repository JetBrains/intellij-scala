package org.jetbrains.sbt.project.modifier

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.psi.{PsiElement, PsiFile, PsiManager}
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.lang.formatting.ScalaFormatterUtil
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.jetbrains.sbt.project.modifier.SimpleBuildFileModifier._
import org.jetbrains.sbt.project.modifier.location._

import scala.collection.mutable

/**
 * Adds dependencies, resolvers and options to the build file in given location.<br>
 * Supposes that all changes of the same type should be added as Seq(...) additions to library dependencies/resovlers/scalacOptions.
 *
 * TODO: try to detect latest library version and use it instead of "latest.integration"
 * TODO: insert changes directly in module definition instead of just appending it to the `build.sbt` file
 *  Though it's not a trivial task for SBT definitions, comparing to e.g. Maven
 */
class SimpleBuildFileModifier(val libDependencies: Seq[String],
                              val resolvers: Seq[String],
                              val scalacOptions: Seq[String],
                              val buildFileProviders: List[BuildFileProvider] = List(SimpleModuleBuildFileProvider, ProjectRootBuildFileProvider),
                              val buildFileLocationProviders: List[BuildFileModificationLocationProvider] = List(EndOfFileLocationProvider))
    extends BuildFileModifier {

  /**
   * Performs some specific modification(s) of sbt build file(s) (library dependencies, resolvers, sbt options, etc.)
   * @param module - module within IJ project to modify build file(s) for
   */
  override protected def modifyInner(module: Module, fileToWorkingCopy: mutable.Map[VirtualFile, LightVirtualFile]): Option[List[VirtualFile]] = {
    val empty: Option[List[VirtualFile]] = Some(List())
    requiredElementTypes.foldLeft(empty)((acc, nextType) =>
      acc match{
        case Some(accList) =>
          inWriteAction {
            addElements(module, nextType, fileToWorkingCopy).map(_ :: accList)
          }
        case _ => None
      })
  }

  //TODO: rewrite this? (the original comment is dated 2015)
  private def addElements(
    module: Module,
    elementType: BuildFileElementType,
    fileToWorkingCopy: mutable.Map[VirtualFile, LightVirtualFile]
  ): Option[VirtualFile] = {
    def newLineElement = createNewLine()(PsiManager.getInstance(module.getProject))

    val buildFiles = buildFileProviders.flatMap(_.findBuildFile(module, elementType, fileToWorkingCopy))
    //NOTE: using lazy collections to add elements only to the first successful file, and not touch other files
    val editedFiles = for {
      buildFileEntry <- buildFiles.iterator
      locationProvider <- buildFileLocationProviders.iterator
      //TODO: we must not use IntelliJ module name here
      // Suppose we have `build.sbt` with content `lazy val root = (project in file(".")).settings(name := "my name")`
      // Module name will be "my name" but in SBT we should use `root`, not "my name"
      // However it's not even clear if we have information about the original `val` name for the module.
      // Maybe SBT provide this information using some macro?
      inName = if (buildFileEntry.isModuleLocal) Nil else Seq(module.getName)
      psiElement <- buildPsiElement(module.getProject, inName, elementType)
      virtualFile <- {
        addElementsToBuildFile(module, locationProvider, elementType, buildFileEntry.file, Seq(newLineElement, psiElement))
      }
    } yield virtualFile
    editedFiles.nextOption()
  }

  private def buildPsiElement(project: Project, inScope: Seq[String], elementType: BuildFileElementType): Option[PsiElement] = {
    elementType match {
      case BuildFileElementType.libraryDependencyElementId =>
        buildLibraryDependenciesPsi(project, inScope, libDependencies)
      case BuildFileElementType.resolverElementId =>
        buildResolversPsi(project, inScope, resolvers)
      case BuildFileElementType.`scalacOptionsElementId` =>
        buildScalacOptionsPsi(project, inScope :+ "Test", scalacOptions)
      case _ =>
        throw new IllegalArgumentException("Unsupported build file element type: " + elementType)
    }
  }

  private def requiredElementTypes: Seq[BuildFileElementType] = {
    supportedElementTypes.filter{
      case BuildFileElementType.libraryDependencyElementId => libDependencies.nonEmpty
      case BuildFileElementType.resolverElementId => resolvers.nonEmpty
      case BuildFileElementType.`scalacOptionsElementId` => scalacOptions.nonEmpty
      case elementType => throw new IllegalArgumentException("Unsupported build file element type: " + elementType)
    }
  }
}

object SimpleBuildFileModifier {

  private def createSeqString(normalIndent: String, seq: Seq[String]): String =
    s"""Seq(
       |${seq.map(normalIndent + _).mkString(",\n")}
       |)""".stripMargin

  private def createSeqPsiExpr(project: Project, inScope: Seq[String], prefix: String, seq: Seq[String]): Option[PsiElement] =
    if (seq.isEmpty) None
    else {
      val valueString = createSeqString(ScalaFormatterUtil.getNormalIndentString(project), seq)
      val scope = if (inScope.isEmpty) Seq("ThisBuild") else inScope
      val scopePath = scope.mkString(" / ")
      val text = s"$scopePath / $prefix ++= $valueString"
      Some(createExpressionFromText(text, ScalaFeatures.default)(PsiManager.getInstance(project)))
    }

  private def buildLibraryDependenciesPsi(project: Project, inScope: Seq[String], dependencies: Seq[String]): Option[PsiElement] =
    createSeqPsiExpr(project, inScope, "libraryDependencies", dependencies)

  private def buildResolversPsi(project: Project, inScope: Seq[String], resolvers: Seq[String]): Option[PsiElement] =
    createSeqPsiExpr(project, inScope, "resolvers", resolvers)

  private def buildScalacOptionsPsi(project: Project, inScope: Seq[String], options: Seq[String]): Option[PsiElement] =
    createSeqPsiExpr(project, inScope, "scalacOptions", options)

  private val supportedElementTypes: List[BuildFileElementType] = List(BuildFileElementType.libraryDependencyElementId,
    BuildFileElementType.resolverElementId, BuildFileElementType.scalacOptionsElementId)

  private def addElementsToBuildFile(
    module: Module,
    locationProvider: BuildFileModificationLocationProvider,
    elementType: BuildFileElementType,
    buildFile: PsiFile,
    psiElements: Seq[PsiElement]
  ): Option[VirtualFile] = {
    val location = locationProvider.getAddElementLocation(module, elementType, buildFile)
    location match {
      case Some((parent, index)) if (index == 0) || parent.getChildren.length >= index =>
        val children = parent.getChildren
        if (children.isEmpty) {
          for (psiElement <- psiElements) {
            parent.add(psiElement)
          }
        } else if (index == 0) {
          for (psiElement <- psiElements) {
            parent.addBefore(psiElement, children(0))
          }
        } else {
          for (psiElement <- psiElements.reverse) {
            parent.addAfter(psiElement, children(index - 1))
          }
        }
        val psiFile = parent.getContainingFile
        val res = psiFile.getVirtualFile
        //TODO: this 'saveText' seems extremely weird here
        //it is needed so that the virtual file gets modified even though we are already inside a write action when
        //modification is performed from setupLibrary (see org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework)
        VfsUtil.saveText(res, psiFile.getText)
        Some(res)
      case _ => None
    }
  }

}