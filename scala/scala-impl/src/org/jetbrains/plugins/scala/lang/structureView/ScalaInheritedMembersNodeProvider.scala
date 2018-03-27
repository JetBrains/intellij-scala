package org.jetbrains.plugins.scala.lang.structureView

import java.util

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.FileStructureNodeProvider
import com.intellij.ide.util.treeView.smartTree.{ActionPresentation, ActionPresentationData, TreeElement}
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.util.IconLoader
import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalSignature
import org.jetbrains.plugins.scala.lang.structureView.element.{ScalaStructureViewElement, ScalaTypeDefinitionStructureViewElement}

import scala.collection.JavaConverters._

/**
 * @author Alefas
 * @since 04.05.12
 */

class ScalaInheritedMembersNodeProvider extends FileStructureNodeProvider[TreeElement] {
  override def provideNodes(node: TreeElement): util.Collection[TreeElement] = {
    node match {
      case td: ScalaTypeDefinitionStructureViewElement =>
        val children = new util.ArrayList[TreeElement]()
        val clazz = td.element
        try {
          if (!clazz.isValid) return children
          val signs = clazz.allSignatures
          for (sign <- signs) {
            sign match {
              case sign: PhysicalSignature =>
                sign.method match {
                  case x if x.name == "$tag" || x.name == "$init$" =>
                  case x if x.containingClass == clazz =>
                  case x: ScFunction => children.addAll(ScalaStructureViewElement(x, inherited = true).asJava)
                  case x: PsiMethod => children.add(new PsiMethodTreeElementDecorator(x, true))
                }
              case _ =>
                sign.namedElement match {
                  case parameter: ScClassParameter if parameter.isEffectiveVal && parameter.containingClass != clazz && !sign.name.endsWith("_=") =>
                    children.addAll(ScalaStructureViewElement(parameter, inherited = true).asJava)
                  case named: ScNamedElement => ScalaPsiUtil.nameContext(named) match {
                    case x: ScValue if x.containingClass != clazz => children.addAll(ScalaStructureViewElement(named, inherited = true).asJava)
                    case x: ScVariable if x.containingClass != clazz => children.addAll(ScalaStructureViewElement(named, inherited = true).asJava)
                    case _ =>
                  }
                  case _ =>
                }
            }
          }
          val types = clazz.allTypeAliases
          for {
            typex <- types
            t = typex._1
            if t.isInstanceOf[ScTypeAlias]
            alias = t.asInstanceOf[ScTypeAlias]
            if alias.containingClass != clazz
          } children.addAll(ScalaStructureViewElement(alias, inherited = true).asJava)

          children
        }
        catch {
          case _: IndexNotReadyException => new util.ArrayList[TreeElement]()
        }
      case _ => new util.ArrayList[TreeElement]()
    }
  }

  override def getCheckBoxText: String = IdeBundle.message("file.structure.toggle.show.inherited")

  override def getShortcut: Array[Shortcut] = KeymapManager.getInstance.getActiveKeymap.getShortcuts("FileStructurePopup")

  override def getPresentation: ActionPresentation = new ActionPresentationData(
    IdeBundle.message("action.structureview.show.inherited"), null, IconLoader.getIcon("/hierarchy/supertypes.png"))

  override def getName: String = "SCALA_SHOW_INHERITED"
}
