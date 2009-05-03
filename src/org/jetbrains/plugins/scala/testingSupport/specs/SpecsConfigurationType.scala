package org.jetbrains.plugins.scala.testingSupport.specs

import _root_.java.lang.String
import _root_.javax.swing.Icon
import com.intellij.execution._
import com.intellij.execution.configurations.{RunConfiguration, ConfigurationType, ConfigurationFactory}
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{JavaPsiFacade, PsiElement}
import icons.Icons
import lang.psi.api.toplevel.typedef.ScTypeDefinition
import script.ScalaScriptRunConfigurationFactory
/**
 * User: Alexander Podkhalyuzin
 * Date: 03.05.2009
 */


/**
 * User: Alexander Podkhalyuzin
 * Date: 22.02.2009
 */

class SpecsConfigurationType extends LocatableConfigurationType {
  val confFactory = new SpecsRunConfigurationFactory(this)

  def getIcon: Icon = Icons.SCALA_TEST

  def getDisplayName: String = "Specs"

  def getConfigurationTypeDescription: String = "Specs testing framework run configuration"

  def getConfigurationFactories: Array[ConfigurationFactory] = Array[ConfigurationFactory](confFactory)

  def getId: String = "SpecsRunConfiguration"


  def createConfigurationByLocation(location: Location[_ <: PsiElement]): RunnerAndConfigurationSettings = {
    val element = location.getPsiElement
    val parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition], false)
    if (parent == null) return null
    val facade = JavaPsiFacade.getInstance(element.getProject)
    val suiteClazz = facade.findClass("org.specs.Specification", GlobalSearchScope.allScope(element.getProject))
    if (suiteClazz == null) return null
    if (!parent.isInheritor(suiteClazz, true)) return null
    val settings = RunManager.getInstance(location.getProject).createRunConfiguration(parent.getName, confFactory)
    settings.getConfiguration.asInstanceOf[SpecsRunConfiguration].setTestClassPath(parent.getQualifiedName)
    return settings
  }

  def isConfigurationByLocation(configuration: RunConfiguration, location: Location[_ <: PsiElement]): Boolean = {
    val element = location.getPsiElement
    val parent: ScTypeDefinition = PsiTreeUtil.getParentOfType(element, classOf[ScTypeDefinition])
    if (parent == null) return false
    val facade = JavaPsiFacade.getInstance(element.getProject)
    val suiteClazz = facade.findClass("org.specs.Specification", GlobalSearchScope.allScope(element.getProject))
    if (suiteClazz == null) return false
    if (!parent.isInheritor(suiteClazz, true)) return false
    configuration match {
      case configuration: SpecsRunConfiguration => return parent.getQualifiedName == configuration.getTestClassPath
      case _ => return false
    }
  }
}