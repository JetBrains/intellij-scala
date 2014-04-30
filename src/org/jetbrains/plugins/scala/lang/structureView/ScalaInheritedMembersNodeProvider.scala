package org.jetbrains.plugins.scala.lang.structureView

import com.intellij.ide.util.FileStructureNodeProvider
import com.intellij.openapi.actionSystem.Shortcut
import elements.impl._
import com.intellij.ide.IdeBundle
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.ide.util.treeView.smartTree.{ActionPresentationData, TreeElement, ActionPresentation}
import com.intellij.openapi.util.IconLoader
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalSignature
import com.intellij.psi.PsiMethod
import com.intellij.ide.structureView.impl.java.PsiMethodTreeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScVariable, ScValue, ScFunction}
import com.intellij.openapi.project.IndexNotReadyException
import org.jetbrains.plugins.scala.extensions.{toPsiClassExt, toPsiMemberExt, toPsiNamedElementExt}
import java.util

/**
 * @author Alefas
 * @since 04.05.12
 */

class ScalaInheritedMembersNodeProvider extends FileStructureNodeProvider[TreeElement] {
  def provideNodes(node: TreeElement): util.Collection[TreeElement] = {
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
                  case x if x.containingClass.qualifiedName == "java.lang.Object" =>
                  case x if x.containingClass == clazz =>
                  case x: ScFunction => children.add(new ScalaFunctionStructureViewElement(x, true))
                  case x: PsiMethod => children.add(new PsiMethodTreeElement(x, true))
                }
              case _ =>
                sign.namedElement match {
                  case named: ScNamedElement => ScalaPsiUtil.nameContext(named) match {
                    case x: ScValue if x.containingClass != clazz => children.add(new ScalaValueStructureViewElement(named.nameId, true))
                    case x: ScVariable if x.containingClass != clazz => children.add(new ScalaVariableStructureViewElement(named.nameId, true))
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
          } children.add(new ScalaTypeAliasStructureViewElement(alias, true))

          children
        }
        catch {
          case e: IndexNotReadyException => new util.ArrayList[TreeElement]()
        }
      case _ => new util.ArrayList[TreeElement]()
    }
  }

  def getCheckBoxText: String = IdeBundle.message("file.structure.toggle.show.inherited")

  def getShortcut: Array[Shortcut] = KeymapManager.getInstance.getActiveKeymap.getShortcuts("FileStructurePopup")

  def getPresentation: ActionPresentation = new ActionPresentationData(
    IdeBundle.message("action.structureview.show.inherited"), null, IconLoader.getIcon("/hierarchy/supertypes.png"))

  def getName: String = "SCALA_SHOW_INHERITED"
}