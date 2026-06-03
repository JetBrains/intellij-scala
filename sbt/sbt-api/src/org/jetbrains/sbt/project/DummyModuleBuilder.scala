package org.jetbrains.sbt.project

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.ModuleType
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * This is a workaround for IDEA-287489
 *
 * Dummy module builder which is meant to be returned from instances of [[com.intellij.openapi.module.ModuleType]]
 * ModuleType is an outdated concept. It's usage should be replaced with alternative mechanisms.
 * It has a mandatory method `createModuleBuilder` which must return some builder.
 * If we return [[com.intellij.ide.util.projectWizard.EmptyModuleBuilder]]
 * then in NPW there will be a redundant generator node "Multi-module Project"
 */
//noinspection ScalaExtractStringToBundle
@Internal
final class DummyModuleBuilder extends ModuleBuilder {
  override def isAvailable: Boolean = false
  override def isOpenProjectSettingsAfter = false
  override def canCreateModule = false
  override def getModuleType: ModuleType[_ <: ModuleBuilder] = ModuleType.EMPTY.asInstanceOf[ModuleType[_ <: ModuleBuilder]]
  override def getPresentableName: String = ""
  override def getGroupName: String = ""
  override def isTemplateBased = false
  override def getDescription: String = ""
}
