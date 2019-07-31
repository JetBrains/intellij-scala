package org.jetbrains.plugins.scala
package projectView

import java.{util => ju}

import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import com.intellij.openapi.project.Project
import javax.swing.Icon
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * @author Pavel Fatin
 */
private sealed abstract class FileNode(protected val file: ScalaFile,
                                       protected val icon: Icon)
                                      (implicit project: Project, settings: ViewSettings)
  extends PsiFileNode(project, file, settings) {

  import collection.JavaConverters._

  override def getChildrenImpl: ju.Collection[Node] =
    if (settings.isShowMembers)
      file.typeDefinitions.map(new TypeDefinitionNode(_): Node).asJava
    else
      ju.Collections.emptyList()

  override protected def updateImpl(data: PresentationData): Unit = {
    super.updateImpl(data)
    data.setIcon(icon)
  }
}

private object FileNode {

  final class ScalaFileNode(override protected val file: ScalaFile)
                           (implicit project: Project, settings: ViewSettings)
    extends FileNode(file, ScalaFileType.INSTANCE.getIcon) {

    override protected def updateImpl(data: PresentationData): Unit = {
      super.updateImpl(data)

      val presentableText = file.getName.stripSuffix(ScalaFileType.INSTANCE.getExtensionWithDot)
      data.setPresentableText(presentableText)
    }
  }

  final class DialectFileNode(override protected val file: ScalaFile,
                              override protected val icon: Icon)
                             (implicit project: Project, settings: ViewSettings)
    extends FileNode(file, icon)

  final class ScriptFileNode(override protected val file: ScalaFile)
                            (implicit project: Project, settings: ViewSettings)
    extends FileNode(file, icons.Icons.SCRIPT_FILE_LOGO) {

    //noinspection TypeAnnotation
    override def getChildrenImpl = ju.Collections.emptyList[Node]()
  }
}