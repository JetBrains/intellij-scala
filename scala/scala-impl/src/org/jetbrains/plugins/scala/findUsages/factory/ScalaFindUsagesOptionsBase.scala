package org.jetbrains.plugins.scala.findUsages.factory

import com.intellij.find.findUsages.JavaFindUsagesOptions
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class ScalaFindUsagesOptionsBase(project: Project) extends JavaFindUsagesOptions(project)
