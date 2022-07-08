package org.jetbrains.plugins.scala
package projectView

import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import java.{util => ju}
import javax.swing.Icon

private[projectView] sealed abstract class FileNode(protected val file: ScalaFile)
                                                   (implicit project: Project, settings: ViewSettings)
  extends PsiFileNode(project, file, settings) with IconableNode {

  import scala.jdk.CollectionConverters._

  override def getChildrenImpl: ju.Collection[Node] =
    if (settings.isShowMembers)
      file.members.flatMap(buildMemberNodes(_)).asJava
    else
      emptyNodesList

  override protected def updateImpl(data: PresentationData): Unit = {
    super.updateImpl(data)
    setIcon(data)
  }
}

private[projectView] object FileNode {

  final class ScalaFileNode(override protected val file: ScalaFile)
                           (implicit project: Project, settings: ViewSettings)
    extends FileNode(file) {

    override def getIcon(flags: Int): Icon = ScalaFileType.INSTANCE.getIcon

    override protected def updateImpl(data: PresentationData): Unit = {
      super.updateImpl(data)

      val presentableText = file.getName.stripSuffix(ScalaFileType.INSTANCE.getExtensionWithDot)
      data.setPresentableText(presentableText)
    }
  }

  final class DialectFileNode(override protected val file: ScalaFile,
                              fileType: FileType)
                             (implicit project: Project, settings: ViewSettings)
    extends FileNode(file) {

    override def getIcon(flags: Int): Icon = fileType.getIcon
  }

  final class ScriptFileNode(override protected val file: ScalaFile)
                            (implicit project: Project, settings: ViewSettings)
    extends FileNode(file) {

    override def getIcon(flags: Int): Icon = icons.Icons.SCRIPT_FILE_LOGO

    //noinspection TypeAnnotation
    override def getChildrenImpl = ju.Collections.emptyList[Node]()
  }
}