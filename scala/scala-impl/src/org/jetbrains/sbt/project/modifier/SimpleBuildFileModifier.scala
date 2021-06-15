package org.jetbrains.sbt.project.modifier

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.psi.{PsiElement, PsiFile, PsiManager}
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.plugins.scala.lang.formatting.FormatterUtil
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.sbt.project.modifier.location._

import scala.collection.mutable

/**
 * Adds dependencies, resolvers and options to the build file in given location. Supposes that all changes of the same
 * type should be added as Seq(...) additions to library dependencies/resovlers/scalacOptions.
 *
 * @author Roman.Shein
 * @since 16.03.2015.
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
        case Some(accList) => addElements(module, nextType, fileToWorkingCopy).map(_ :: accList)
        case _ => None
      })
  }

  protected def addElements(module: Module, elementType: BuildFileElementType,
                            fileToWorkingCopy: mutable.Map[VirtualFile, LightVirtualFile]): Option[VirtualFile] = {
    val locationProvidersStream = buildFileLocationProviders.to(LazyList)
    //TODO: rewrite this?
    buildFileProviders.map(fileProvider =>
      fileProvider.findBuildFile(module, elementType, fileToWorkingCopy)).to(LazyList).map(_.map(buildFileEntry =>
      locationProvidersStream.map(locationProvider => buildPsiElement(module.getProject,
        Option(if (buildFileEntry.isModuleLocal) null else module.getName), elementType).map(
            SimpleBuildFileModifier.addElementsToBuildFile(module, locationProvider,elementType, buildFileEntry.file,
              createNewLine()(PsiManager.getInstance(module.getProject)), _)
        )).find(_.isDefined).flatten
      )).map(opt => opt.flatten.flatten).find(_.isDefined).flatten
  }

  protected def buildPsiElement(project: Project, inName: Option[String], elementType: BuildFileElementType): Option[PsiElement] = {
    elementType match {
      case BuildFileElementType.libraryDependencyElementId =>
        SimpleBuildFileModifier.buildLibraryDependenciesPsi(project, inName, libDependencies)
      case BuildFileElementType.resolverElementId =>
        SimpleBuildFileModifier.buildResolversPsi(project, inName, resolvers)
      case BuildFileElementType.`scalacOptionsElementId` =>
        SimpleBuildFileModifier.buildScalacOptionsPsi(project, Some("Test"), scalacOptions)
      case _ => throw new IllegalArgumentException("Unsupported build file element type: " + elementType)
    }
  }

  protected def requiredElementTypes: Seq[BuildFileElementType] = {
    SimpleBuildFileModifier.supportedElementTypes.filter{
      case BuildFileElementType.libraryDependencyElementId => libDependencies.nonEmpty
      case BuildFileElementType.resolverElementId => resolvers.nonEmpty
      case BuildFileElementType.`scalacOptionsElementId` => scalacOptions.nonEmpty
      case elementType => throw new IllegalArgumentException("Unsupported build file element type: " + elementType)
    }
  }
}

object SimpleBuildFileModifier {

  def createSeqString(normalIndent: String, seq: Seq[String]): String =
    "Seq(\n" + seq.tail.fold(normalIndent + seq.head)(_ + ",\n" + normalIndent + _) + "\n)"

  def createSeqPsiExpr(project: Project, inName: Option[String], prefix: String, seq: Seq[String]): Option[PsiElement] =
    if (seq.isEmpty) None
    else Some(createExpressionFromText(prefix + inName.map(" in " + _).getOrElse("") + " ++= " +
      createSeqString(FormatterUtil.getNormalIndentString(project), seq))(PsiManager.getInstance(project)))

  def buildLibraryDependenciesPsi(project: Project, inName: Option[String], dependencies: Seq[String]): Option[PsiElement] =
    createSeqPsiExpr(project, inName, "libraryDependencies", dependencies)

  def buildResolversPsi(project: Project, inName: Option[String], resolvers: Seq[String]): Option[PsiElement] =
    createSeqPsiExpr(project, inName, "resolvers", resolvers)

  def buildScalacOptionsPsi(project: Project, inName: Option[String], options: Seq[String]): Option[PsiElement] =
    createSeqPsiExpr(project, inName, "scalacOptions", options)

  val supportedElementTypes: List[BuildFileElementType] = List(BuildFileElementType.libraryDependencyElementId,
    BuildFileElementType.resolverElementId, BuildFileElementType.scalacOptionsElementId)

  def addElementsToBuildFile(module: Module, locationProvider: BuildFileModificationLocationProvider,
                             elementType: BuildFileElementType, buildFile: PsiFile, psiElements: PsiElement*): Option[VirtualFile] = {
    locationProvider.getAddElementLocation(module, elementType, buildFile) match {
      case Some((parent, index)) if (index == 0) || parent.getChildren.length >= index =>
        val children = parent.getChildren
        if (children.isEmpty) {
          for (psiElement <- psiElements) parent.add(psiElement)
        } else if (index == 0) {
          for (psiElement <- psiElements) parent.addBefore(psiElement, children(0))
        } else {
          for (psiElement <- psiElements.reverse) parent.addAfter(psiElement, children(index - 1))
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

  def addElementToBuildFile(module: Module, locationProvider: BuildFileModificationLocationProvider,
                            elementType: BuildFileElementType, buildFile: PsiFile, psiElement: PsiElement): Option[VirtualFile] = {
    addElementsToBuildFile(module, locationProvider, elementType, buildFile, psiElement)
  }

  def removeElementFromBuildFile(module: Module, locationProvider: BuildFileModificationLocationProvider,
                                 buildFile: PsiFile, elementType: BuildFileElementType, elementCondition: PsiElement => Boolean): Option[PsiFile] = {
    locationProvider.getModifyOrRemoveElement(module, elementType, elementCondition, buildFile) match {
      case Some(element) =>
        val res = element.getContainingFile
        element.delete()
        Some(res)
      case None => None
    }
  }

  def modifyElementInBuildFile(module: Module, locationProvider: BuildFileModificationLocationProvider,
                               elementType: BuildFileElementType, buildFile: PsiFile,
                               elementCondition: PsiElement => Boolean, modifyFunction: PsiElement => PsiElement): Option[PsiFile] = {
    locationProvider.getModifyOrRemoveElement(module, elementType, elementCondition, buildFile) match {
      case Some(element) =>
        val res = element.getContainingFile
        element.replace(modifyFunction(element))
        Some(res)
      case None => None
    }
  }
}