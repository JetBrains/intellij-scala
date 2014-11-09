package org.jetbrains.sbt
package codeInsight.template

import com.intellij.codeInsight.template.FileTypeBasedContextType
import org.jetbrains.sbt.language.SbtFileType

/**
 * @author Nikolay Obedin
 * @since 7/31/14.
 */
class SbtLiveTemplateContextType extends FileTypeBasedContextType("SBT", "SBT", SbtFileType)

