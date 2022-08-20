package org.jetbrains.plugins.scala.testingSupport.test.munit

import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer.CreateFromContextInfo
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestConfigurationProducer.CreateFromContextInfo.{AllInPackage, ClassWithTestName}
import org.jetbrains.plugins.scala.testingSupport.test.TestConfigurationUtil.isInheritor
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestSingleTestLocationFinderOld

final class MUnitConfigurationProducer extends AbstractTestConfigurationProducer {

  override val suitePaths: Seq[String] = MUnitTestFramework().baseSuitePaths

  override val getConfigurationFactory: ConfigurationFactory = MUnitConfigurationType().confFactory

  override protected def configurationName(contextInfo: CreateFromContextInfo): String = contextInfo match {
    case AllInPackage(_, packageName)           =>
      s"UTest in '$packageName'"
    case ClassWithTestName(testClass, testName) =>
      StringUtil.getShortName(testClass.qualifiedName) + testName.fold("")("." + _)
  }

  override def getTestClassWithTestName(location: PsiElementLocation): Option[ClassWithTestName] = {
    val element = location.getPsiElement

    element match {
      case file: ScalaFile =>
        val clazz = file.typeDefinitions.filter(matchesSomeTestSuite) match {
          case Seq(testClass) => Some(testClass) // run multiple test classes in a file is not supported yet, see SCL-15567
          case _ => None
        }
        return clazz.map(ClassWithTestName(_, None))
      case _ =>
    }

    def matchesSomeTestSuite(typ: ScTemplateDefinition): Boolean = suitePaths.exists(isInheritor(typ, _))

    import org.jetbrains.plugins.scala.extensions.IteratorExt
    val parentClasses = element.withParentsInFile.filterByType[ScTypeDefinition]
    val suiteClass = parentClasses.filter(matchesSomeTestSuite).nextOption() match {
      case Some(value) =>
        value
      case _ =>
        return None
    }

    val testName = suiteClass.extendsBlock.templateBody match {
      case Some(templateBody) =>
        val finder = new ScalaTestSingleTestLocationFinderOld(element, suiteClass, templateBody)
        finder.findTestNameForMUnit()
      case _ =>
        None
    }
    val result = ClassWithTestName(suiteClass, testName)
    Some(result)
  }
}

object MUnitConfigurationProducer {

  def apply(): MUnitConfigurationProducer =
    RunConfigurationProducer.EP_NAME.findExtensionOrFail(classOf[MUnitConfigurationProducer])
}