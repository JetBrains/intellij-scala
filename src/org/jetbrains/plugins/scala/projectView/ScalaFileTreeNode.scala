package org.jetbrains.plugins.scala.projectView

import java.util

import com.intellij.ide.projectView.impl.nodes.{ClassTreeNode, PsiFileNode}
import com.intellij.ide.projectView.{PresentationData, ViewSettings}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import scala.collection.JavaConverters._

/**
  * @author Pavel Fatin
  */
class ScalaFileTreeNode(file: ScalaFile, settings: ViewSettings)
  extends PsiFileNode(file.getProject, file, settings) {

  override def getChildrenImpl: util.Collection[Node] =
    file.isScriptFile.fold(Seq.empty, file.typeDefinitions)
      .map(it => new TypeDefinitionNode(new ClassTreeNode(getProject, it, settings)): Node)
      .asJava

  override protected def updateImpl(data: PresentationData): Unit = {
    super.updateImpl(data)

    val icon = if (file.isScriptFile) Icons.SCRIPT_FILE_LOGO else Icons.FILE

    data.setPresentableText(getValue.getName)
    data.setIcon(icon)
  }
}
