package org.jetbrains.plugins.scala
package lang
package psi
package compiled

import com.intellij.debugger.{DebuggerManager, NameMapper}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.compiled.ScalaCompilerLoader._

class ScalaCompilerLoader extends StartupActivity.DumbAware {
  override def runActivity(project: Project): Unit = {
    if (!isDisabled) {
      val mapper = new ScalaNameMapper
      DebuggerManager.getInstance(project).addClassNameMapper(mapper)

      invokeOnScalaPluginUnload(project) {
        DebuggerManager.getInstance(project).removeClassNameMapper(mapper)
      }
    }
  }
}

object ScalaCompilerLoader {
  private[compiled] def isDisabled: Boolean = {
    val application = ApplicationManager.getApplication
    !application.isUnitTestMode &&
      // The following check is hardly bulletproof, however (currently) there is no API to query that
      application.getClass.getSimpleName.contains("Upsource")
  }

  private class ScalaNameMapper extends NameMapper {
    override def getQualifiedName(clazz: PsiClass): String = clazz match {
      case tmpl: ScTemplateDefinition => inReadAction { javaName(tmpl) }
      case _ => null
    }
  }

  private def javaName(clazz: ScTemplateDefinition): String = {
    clazz.qualifiedName + (if (clazz.isInstanceOf[ScObject]) "$" else "")
  }
}
