package org.jetbrains.plugins.scala.configurations

import com.intellij.execution.configurations.RunConfiguration

/**
 * Contains any kind of information needed to create a run configuration that's usually filled in by the user via UI.
 * At least it serves an approximation of the user intent and what users operate with in UI.
 *
 * @param location             describes in the UI user initiated test creation.
 *                             For example, right-click inside a file or ont a file/directory/package in the project
 * @param preferredConfigClass if specified, the run configuration of this class will be used instead of the default one.
 *                             It's used in the cases when several run configurations of different types can be created in the same location.
 *                             For example, there can be both junit and scalatest tests in the same directory.
 *                             It's implied that if this is specified then multiple run configurations can be created in the same context (though with different types).
 */
case class RunConfigCreationContext(
  location: RunConfigCreationLocation,
  preferredConfigClass: Option[Class[_ <: RunConfiguration]] = None
)
