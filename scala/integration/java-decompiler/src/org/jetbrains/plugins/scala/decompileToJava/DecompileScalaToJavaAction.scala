package org.jetbrains.plugins.scala.decompileToJava

import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, CommonDataKeys}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class DecompileScalaToJavaAction extends AnAction("Decompile Scala to Java") {
  override def actionPerformed(event: AnActionEvent): Unit =
    getClassfile(event).foreach(ScalaBytecodeDecompileTask.showDecompiledJavaCode)

  override def update(event: AnActionEvent): Unit = {
    val project     = event.getProject
    val classFile   = getClassfile(event)
    event.getPresentation().setEnabledAndVisible(project != null && classFile.isDefined)
  }

  private[this] def getClassfile(event: AnActionEvent): Option[ScalaFile] =
    selectedClassfile(event).orElse(currentlyOpenClassfile(event))

  private[this] def selectedClassfile(event: AnActionEvent): Option[ScalaFile] =
    event.getData(CommonDataKeys.NAVIGATABLE) match {
      case sfile: ScalaFile if sfile.isCompiled => Option(sfile)
      case _                                    => None
    }

  private[this] def currentlyOpenClassfile(event: AnActionEvent): Option[ScalaFile] =
    event.getData(CommonDataKeys.PSI_FILE) match {
      case sfile: ScalaFile if sfile.isCompiled => Option(sfile)
      case _                                    => None
    }
}