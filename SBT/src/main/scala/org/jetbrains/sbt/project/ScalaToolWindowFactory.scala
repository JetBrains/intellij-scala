package org.jetbrains.sbt
package project

import com.intellij.openapi.externalSystem.service.task.ui.AbstractExternalSystemToolWindowFactory

/**
 * @author Pavel Fatin
 */
class ScalaToolWindowFactory extends AbstractExternalSystemToolWindowFactory(SbtProjectSystem.Id)