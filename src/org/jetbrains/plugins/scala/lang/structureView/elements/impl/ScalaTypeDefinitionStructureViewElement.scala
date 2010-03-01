package org.jetbrains.plugins.scala
package lang
package structureView
package elements
package impl

import _root_.org.jetbrains.plugins.scala.lang.psi.types.{FullSignature, PhysicalSignature, ScSubstitutor}
import com.intellij.ide.structureView.impl.java.PsiMethodTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement


import com.intellij.navigation.ItemPresentation
import psi.api.toplevel.ScNamedElement
import psi.impl.toplevel.typedef.TypeDefinitionMembers
import com.intellij.openapi.project.IndexNotReadyException;
import org.jetbrains.plugins.scala.lang.structureView.itemsPresentations.impl._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi._

import _root_.scala.collection.mutable._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.base._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaTypeDefinitionStructureViewElement(private val element: ScTypeDefinition) extends ScalaStructureViewElement(element, false) {

  def getPresentation(): ItemPresentation = {
    return new ScalaTypeDefinitionItemPresentation(element);
  }

  def getChildren(): Array[TreeElement] = {
    val children = new ArrayBuffer[TreeElement]
    val clazz: ScTypeDefinition = element.asInstanceOf[ScTypeDefinition]
    val members = clazz.members
    for (member <- members) {
      member match {
        case func: ScFunction => {
          children += new ScalaFunctionStructureViewElement(func, false)
        }
        case constr: ScPrimaryConstructor => {
          children += new ScalaPrimaryConstructorStructureViewElement(constr)
        }
        case member: ScVariable => {
          for (f <- member.declaredElements)
            children += new ScalaVariableStructureViewElement(f.nameId, false)
        }
        case member: ScValue => {
          for (f <- member.declaredElements)
            children += new ScalaValueStructureViewElement(f.nameId, false)
        }
        case member: ScTypeAlias => {
          children += new ScalaTypeAliasStructureViewElement(member, false)
        }
        case _ =>
      }
    }
    try {
      val signs = clazz.allSignatures
      for (sign <- signs) {
        sign match {
          case FullSignature(sign: PhysicalSignature, _, _, _) => {
            sign.method match {
              case x if x.getName == "$tag" || x.getName == "$init$" =>
              case x if x.getContainingClass.getQualifiedName == "java.lang.Object" =>
              case x if x.getContainingClass == clazz =>
              case x: ScFunction => children += new ScalaFunctionStructureViewElement(x, true)
              case x: PsiMethod => children += new PsiMethodTreeElement(x, true)
            }
          }
          case FullSignature(_, _, element, _) => {
            element match {
              case named: ScNamedElement => ScalaPsiUtil.nameContext(named) match {
                case x: ScValue if x.getContainingClass != clazz => children += new ScalaValueStructureViewElement(named.nameId, true)
                case x: ScVariable if x.getContainingClass != clazz => children += new ScalaVariableStructureViewElement(named.nameId, true)
                case _ =>
              }
            }
          }
        }
      }
      val types = clazz.allTypeAliases
      val t: TypeDefinitionMembers.TypeNodes.T = null
      for {
        typex <- types
        t = typex._1
        if t.isInstanceOf[ScTypeAlias]
        alias = t.asInstanceOf[ScTypeAlias]
        if alias.getContainingClass != clazz
      } children += new ScalaTypeAliasStructureViewElement(alias, true)

      for (typeDef <- element.typeDefinitions)
        children += new ScalaTypeDefinitionStructureViewElement(typeDef)
    }
    catch {
      case e: IndexNotReadyException => //do nothing, this is indexing
    }
    return children.toArray
  }
}