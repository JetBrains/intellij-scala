package org.jetbrains.plugins.scala

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.plugins.scala.project.ProjectExt

/**
  * @author adkozlov
  */
package object codeInspection {

  implicit class ProblemsHolderExt(val holder: ProblemsHolder) extends AnyVal {
    def typeSystem = holder.getProject.typeSystem
  }

}
