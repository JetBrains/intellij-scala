package org.jetbrains.plugins.scala
package debugger

import com.intellij.debugger.{DebuggerManager, NameMapper}
import com.intellij.openapi.application.Application
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.project.{Project, ProjectManager, ProjectManagerListener}
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}

final class ScalaJVMNameMapper private(application: Application) extends NameMapper {

  def getQualifiedName(clazz: PsiClass): String = application.runReadAction {
    (() => clazz match {
      case definition: ScTemplateDefinition =>
        val name = definition.qualifiedName
        definition match {
          case _: ScObject => name + "$"
          case _ => name
        }
      case _ => null
    }): Computable[String]
  }
}

object ScalaJVMNameMapper {

  def apply(application: Application): ScalaJVMNameMapper = {
    val nameMapper = new ScalaJVMNameMapper(application)

    application.getMessageBus.connect().subscribe(
      ProjectManager.TOPIC,
      new ProjectManagerListener {
        override def projectOpened(project: Project): Unit = {
          CompilerManager.getInstance(project).addCompilableFileType(ScalaFileType.INSTANCE)
          DebuggerManager.getInstance(project).addClassNameMapper(nameMapper)
        }
      }
    )

    nameMapper
  }
}