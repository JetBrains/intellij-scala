package org.jetbrains.plugins.scala.testingSupport.test

import com.intellij.execution.JavaExecutionUtil
import com.intellij.execution.configurations.ModuleBasedConfiguration
import com.intellij.psi.PsiClass
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.testingSupport.test.testdata.{ClassTestData, TestConfigurationData}
import org.jetbrains.sbt.project.extensionPoints.ModuleBasedConfigurationMainClassExtractor

class AbstractTestConfigurationMainClassExtractor extends ModuleBasedConfigurationMainClassExtractor {

  override def getConfigurationMainClass(config: ModuleBasedConfiguration[_, _]): Option[String] =
    config match {
      case x: AbstractTestRunConfiguration => handleTestConfigurationData(x.testConfigurationData)
      case _ => None
    }

  private def handleTestConfigurationData(data: TestConfigurationData): Option[String] =
    data match {
      case data: ClassTestData => getMainClassFromPsiClass(data.getClassPathClazz)
      case _ => None
    }

  private def getMainClassFromPsiClass(@Nullable psiClass: PsiClass): Option[String] =
    if (psiClass != null) Option(JavaExecutionUtil.getRuntimeQualifiedName(psiClass))
    else None
}
