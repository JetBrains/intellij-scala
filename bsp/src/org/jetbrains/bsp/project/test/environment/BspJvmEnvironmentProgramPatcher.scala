package org.jetbrains.bsp.project.test.environment

import com.intellij.execution.Executor
import com.intellij.execution.configurations.{JavaParameters, RunConfiguration, RunProfile}
import com.intellij.execution.runners.JavaProgramPatcher
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.UserDataHolderBase

import scala.collection.JavaConverters._

class BspJvmEnvironmentProgramPatcher extends JavaProgramPatcher {

  val logger = Logger.getInstance(classOf[BspJvmEnvironmentProgramPatcher])

  override def patchJavaParameters(executor: Executor, configuration: RunProfile, javaParameters: JavaParameters): Unit = {
    configuration match {
      case testConfig: UserDataHolderBase with RunConfiguration =>
        if(BspEnvironmentRunnerExtension.isSupported(testConfig)) {
          if(testConfig.getBeforeRunTasks.stream().noneMatch(_.getProviderId == BspFetchEnvironmentTask.runTaskKey)) {
            logger.error("Trying to execute BSP-based RunConfiguration without BSP environment set.")
          }
        }
        val env = testConfig.getUserData(BspFetchEnvironmentTask.jvmEnvironmentKey)
        if (env != null) {
          val oldEnvironmentVariables = javaParameters.getEnv.asScala.toMap
          val newEnvironmentVariables = oldEnvironmentVariables ++ env.environmentVariables
          javaParameters.setEnv(newEnvironmentVariables.asJava)

          val oldClasspath = javaParameters.getClassPath.getPathList.asScala.toList
          val newClassPath = env.classpath ++ oldClasspath
          javaParameters.getClassPath.clear()
          javaParameters.getClassPath.addAll(newClassPath.asJava)

          javaParameters.setWorkingDirectory(env.workdir)
          javaParameters.getVMParametersList.addAll(env.jvmOptions.asJava)
        }
      case _ =>
    }
  }
}
