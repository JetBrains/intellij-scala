package org.jetbrains.plugins.scala.lang.structureView

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.StructureViewBundle
import com.intellij.ide.util.FileStructureNodeProvider
import com.intellij.ide.util.treeView.smartTree.{ActionPresentation, ActionPresentationData, TreeElement}
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalMethodSignature
import org.jetbrains.plugins.scala.lang.structureView.element.Element

import java.util
import java.util.Collections
import scala.jdk.CollectionConverters._

class ScalaInheritedMembersNodeProvider extends FileStructureNodeProvider[TreeElement] {

  override def provideNodes(node: TreeElement): util.Collection[TreeElement] = node match {
    case e: Element => nodesOf(e.element)
    case _ => Collections.emptyList[TreeElement]
  }

  private def nodesOf(element: PsiElement): util.Collection[TreeElement] = {
    element match {
      case clazz: ScTemplateDefinition =>
        val children = new util.ArrayList[TreeElement]()
        try {
          if (!clazz.isValid) return children
          for (sign <- clazz.allSignatures) {
            sign match {
              case sign: PhysicalMethodSignature =>
                sign.method match {
                  case x if x.name == "$tag" || x.name == "$init$" =>
                  case x if x.containingClass == clazz =>
                  case x: ScFunction => children.addAll(Element.forPsi(x, inherited = true).asJava)
                  case x: PsiMethod => children.add(new PsiMethodTreeElementDecorator(x, true))
                }
              case _ =>
                sign.namedElement match {
                  case parameter: ScClassParameter if parameter.isClassMember && parameter.containingClass != clazz && !sign.name.endsWith("_=") =>
                    children.addAll(Element.forPsi(parameter, inherited = true).asJava)
                  case named: ScNamedElement => named.nameContext match {
                    case x: ScValue if x.containingClass != clazz => children.addAll(Element.forPsi(named, inherited = true).asJava)
                    case x: ScVariable if x.containingClass != clazz => children.addAll(Element.forPsi(named, inherited = true).asJava)
                    case _ =>
                  }
                  case _ =>
                }
            }
          }
          clazz.allTypeSignatures.map(_.namedElement).collect {
            case alias: ScTypeAlias if alias.containingClass != clazz =>
              children.addAll(Element.forPsi(alias, inherited = true).asJava)
          }

          children
        }
        catch {
          case _: IndexNotReadyException => Collections.emptyList[TreeElement]
        }
      case _ => Collections.emptyList[TreeElement]
    }
  }

  override def getCheckBoxText: String = StructureViewBundle.message("file.structure.toggle.show.inherited")

  override def getShortcut: Array[Shortcut] = KeymapManager.getInstance.getActiveKeymap.getShortcuts("FileStructurePopup")

  override def getPresentation: ActionPresentation = new ActionPresentationData(
    StructureViewBundle.message("action.structureview.show.inherited"), null, AllIcons.Hierarchy.Supertypes)

  override def getName: String = ScalaInheritedMembersNodeProvider.ID
}

object ScalaInheritedMembersNodeProvider {
  val ID = "SCALA_SHOW_INHERITED"
}
