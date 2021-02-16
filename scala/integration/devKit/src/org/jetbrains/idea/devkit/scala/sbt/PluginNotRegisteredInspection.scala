package org.jetbrains.idea.devkit.scala.sbt

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.xml.XmlFileImpl
import com.intellij.psi.search.{FilenameIndex, GlobalSearchScope}
import com.intellij.psi.xml.XmlFile
import org.jetbrains.idea.devkit.dom.Dependency
import org.jetbrains.idea.devkit.util.DescriptorUtil
import org.jetbrains.plugins.scala.codeInspection.AbstractRegisteredInspection
import org.jetbrains.plugins.scala.codeInspection.collections._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScPatternDefinition

import scala.collection.immutable.ArraySeq.unsafeWrapArray
import scala.collection.mutable
import scala.jdk.CollectionConverters._


class PluginNotRegisteredInspection extends AbstractRegisteredInspection {

  private val `:=`          = invocation(Set(":=", "+=", "++=")).from(Seq("sbt.SettingKey"))
  private val PROJECT_TYPE  = "_root_.sbt.Project"

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)(implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    element match {
      case lit: ScLiteral if isInsidePluginsDefinition(lit) && !isRegistered(lit) =>
          super.problemDescriptor(lit, maybeQuickFix, descriptionTemplate, highlightType)
      case _ => None
    }

  }

  private def isInsidePluginsDefinition(lit: ScLiteral): Boolean = {
    def findMatchingParent(elem: PsiElement, depth: Int = 10): Option[PsiElement] = elem match {
      case _ if depth == 0 => None // search limit exceeded
      case expr@(namedRef("intellijPlugins") `:=` _) =>Some(expr)
      case _ => elem.parent.flatMap(findMatchingParent(_, depth-1))
    }

    findMatchingParent(lit).isDefined
  }

  private def isRegistered(lit: ScLiteral): Boolean = {
    val registeredIds = extractRegisteredIds(lit)
    registeredIds.contains(lit.getValue.toString)
  }

  private def extractRegisteredIds(lit: ScLiteral): Set[String] = {
    val maybeRelatedModule = guessEnclosingModule(lit)
    val collectedXmls = maybeRelatedModule
      .flatMap(findPluginXml) match {
      case Some(xmlFile) =>
        Seq(xmlFile)
      case None =>
        unsafeWrapArray(ModuleManager.getInstance(lit.getProject).getModules).flatMap(findPluginXml)
    }

    val descriptors = collectedXmls.map(DescriptorUtil.getIdeaPlugin).filter(_ != null)
    val directDeps: Seq[Dependency] = descriptors.flatMap(_.getDepends.asScala)
    val directDepConfigs: Seq[XmlFile] = directDeps.map(_.getResolvedConfigFile)
    val collector: XmlFile => Seq[Dependency] = dep => DescriptorUtil.getIdeaPlugin(dep).getDepends.asScala.toSeq
    val transitiveDeps = directDepConfigs.flatMap(collector)
    val dependencies = transitiveDeps.map(_.getStringValue).toSet
    dependencies
  }

  private def findPluginXml(module: Module): Option[XmlFile] = {
    unsafeWrapArray(
      FilenameIndex.getFilesByName(module.getProject, "plugin.xml", GlobalSearchScope.moduleScope(module)))
        .collectFirst { case file: XmlFileImpl => file }
  }

  private def guessEnclosingModule(elem: ScExpression): Option[Module] = {
    for {
      maybeDef        <- elem.parentOfType[ScPatternDefinition]
      defTpe          <- maybeDef.`type`().toOption
      projectValName  <- maybeDef.declaredNames.headOption if defTpe.canonicalText == PROJECT_TYPE
      module = ModuleManager.getInstance(elem.getProject).findModuleByName(projectValName)
    } yield module
  }

  class PluginDependencyIndex {

    private val cache: mutable.HashMap[String, Set[String]] = new mutable.HashMap[String, Set[String]]()

    private def doExplicitLookup(project: Project, moduleName: String): Set[String] = {
      unsafeWrapArray(
        FilenameIndex.getFilesByName(project, "plugin.xml", GlobalSearchScope.moduleScope(???)))
        .collectFirst { case file: XmlFileImpl => file }
      ???
    }

    def getIdsForModule(project: Project, moduleName: String): Set[String] = {
      ???
    }
  }

}
