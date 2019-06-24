package org.jetbrains.sbt
package codeInsight
package template

import com.intellij.codeInsight.template.FileTypeBasedContextType

/**
 * @author Nikolay Obedin
 * @since 7/31/14.
 */
final class SbtLiveTemplateContextType extends FileTypeBasedContextType(
  Sbt.Name,
  Sbt.Name,
  language.SbtFileType
)