package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.Executor
import com.intellij.execution.configurations.{JavaParameters, ModuleBasedConfiguration, RunConfigurationModule, RunProfile}
import com.intellij.execution.runners.JavaProgramPatcher

import scala.collection.JavaConverters._

class BspJvmEnvironmentProgramPatcher extends JavaProgramPatcher {
  def patchJavaParameters(executor: Executor, configuration: RunProfile, javaParameters: JavaParameters): Unit = {
    configuration match {
      case testConfig: ModuleBasedConfiguration[RunConfigurationModule, _] =>
        val env = testConfig.getUserData(BspFetchTestEnvironmentTask.jvmTestEnvironmentKey)
        if (env != null) {
          val oldEnvironmentVariables = javaParameters.getEnv.asScala.toMap
          val newEnvironmentVariables = oldEnvironmentVariables ++ env.environmentVariables
          javaParameters.setEnv(newEnvironmentVariables.asJava)

          val oldClasspath = javaParameters.getClassPath.getPathList.asScala.toList
          val newClassPath = env.classpath ++ oldClasspath
          javaParameters.getClassPath.addAll(newClassPath.asJava)

          javaParameters.setWorkingDirectory(env.workdir)
          javaParameters.getVMParametersList.addAll(env.jvmOptions.asJava)
        }
    }
  }
}
