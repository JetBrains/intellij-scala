package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.service.settings.AbstractExternalSystemToolWindowCondition

/**
  * @author Pavel Fatin
  */
class ScalaToolWindowFactoryCondition extends AbstractExternalSystemToolWindowCondition(SbtProjectSystem.Id)