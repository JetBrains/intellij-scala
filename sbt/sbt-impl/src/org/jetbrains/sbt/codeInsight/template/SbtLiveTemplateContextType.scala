package org.jetbrains.sbt
package codeInsight
package template

import com.intellij.codeInsight.template.FileTypeBasedContextType

final class SbtLiveTemplateContextType extends FileTypeBasedContextType(Sbt.Name, language.SbtFileType)
