package org.jetbrains.plugins.scala
package projectView

import java.util

import com.intellij.ide.projectView.{TreeStructureProvider, ViewSettings}
import com.intellij.openapi.project.{DumbAware, Project}
import org.jetbrains.plugins.scala.extensions.PsiModifierListOwnerExt
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * @author Pavel Fatin
 */
final class ScalaTreeStructureProvider extends TreeStructureProvider with DumbAware {

  import ScalaTreeStructureProvider._

  import collection.JavaConverters._

  override def modify(parent: Node, children: util.Collection[Node], settings: ViewSettings): util.Collection[Node] =
    children.asScala.map { it =>
      transform(it)(it.getProject, settings)
    }.asJavaCollection
}

private object ScalaTreeStructureProvider {

  private def transform(node: Node)
                       (implicit project: Project, settings: ViewSettings): Node = node.getValue match {
    case file: ScalaFile => nodeFor(file)

    case definition: ScTypeDefinition if !node.isInstanceOf[TypeDefinitionNode] =>
      new TypeDefinitionNode(definition)

    case _ => node
  }

  import FileNode._

  private[this] def nodeFor(file: ScalaFile)
                           (implicit project: Project, settings: ViewSettings): Node = file.getFileType match {
    case ScalaFileType.INSTANCE =>
      if (file.isScriptFile)
        new ScriptFileNode(file)
      else
        file match {
          case SingularDefinition(definition) =>
            if (definition.isPackageObject) new PackageObjectNode(definition)
            else new TypeDefinitionNode(definition)

          case ClassAndCompanionObject(classDefinition, _) if !settings.isShowMembers =>
            val icon = if (classDefinition.hasAbstractModifier) Icons.ABSTRACT_CLASS_AND_OBJECT else Icons.CLASS_AND_OBJECT
            new CustomDefinitionNode(classDefinition, classDefinition.decorate(icon, 0))

          case TraitAndCompanionObject(traitDefinition, _) if !settings.isShowMembers =>
            new CustomDefinitionNode(traitDefinition, Icons.TRAIT_AND_OBJECT)

          case _ => new ScalaFileNode(file)
        }
    case fileType => new DialectFileNode(file, fileType.getIcon)
  }
}
