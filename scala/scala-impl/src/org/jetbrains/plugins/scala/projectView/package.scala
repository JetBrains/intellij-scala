package org.jetbrains.plugins.scala

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}

package object projectView {

  private[projectView] type Node = AbstractTreeNode[_]

  private[projectView] object Node {

    import FileNode._

    def apply(file: ScalaFile)
             (implicit project: Project, settings: ViewSettings): Node with IconableNode = {
      val fileType = file.getFileType
      val isMill = isMillFile(file)
      fileType match {
        case _ if isMill =>
          new MillFileNode(file)
        case ScalaFileType.INSTANCE =>
          val fileKind = FileKind.getForFile(file)
          val node = fileKind.flatMap(_.node)
          node.getOrElse(new ScalaFileNode(file))
        case fileType               =>
          new DialectFileNode(file, fileType)
      }
    }

    private def isMillFile(file: ScalaFile):Boolean = {
      val psiFile = file.getContainingFile
      if (psiFile == null) return false
      val vf = psiFile.getVirtualFile
      if (vf == null) return false
      val fileExt = FileUtilRt.getExtension(vf.getPath)
      val millExt = ScalaBundle.message("scala.file.mill.extension")
      fileExt == millExt
    }
  }

  private[projectView] def buildMemberNodes(member: ScMember)
                                           (implicit project: Project, settings: ViewSettings): Seq[Node] = member match {
    case definition: ScTypeDefinition =>
      Seq(new TypeDefinitionNode(definition))
    case element: ScNamedElement =>
      Seq(new NamedElementNode(element))
    case value: ScValueOrVariable =>
      value.declaredElements.map(new NamedElementNode(_))
    case extension: ScExtension =>
      Seq(new ExtensionNode(extension))
    case _ =>
      Seq.empty
  }
}
