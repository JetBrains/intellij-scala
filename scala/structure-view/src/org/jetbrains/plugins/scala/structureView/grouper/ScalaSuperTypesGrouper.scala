package org.jetbrains.plugins.scala.structureView.grouper

import com.intellij.icons.AllIcons
import com.intellij.ide.structureView.impl.java.SuperTypeGroup
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.smartTree.{ActionPresentation, ActionPresentationData, Group, Grouper, TreeElement}
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.PsiMemberExt
import org.jetbrains.plugins.scala.structureView.ScalaStructureViewBundle
import org.jetbrains.plugins.scala.structureView.element.Element

import java.util
import java.util.Collections.emptyList
import scala.collection.mutable
import scala.jdk.CollectionConverters.SetHasAsJava

object ScalaSuperTypesGrouper extends Grouper {
  val ID: String = "SCALA_GROUP_BY_DEFINING_TYPE"

  override def getName: String = ID

  override def getPresentation: ActionPresentation = ActionPresentationData(
    ScalaStructureViewBundle.message("group.members.by.defining.type"),
    null,
    AllIcons.General.ImplementingMethod
  )

  override def group(parent: AbstractTreeNode[_], children: util.Collection[TreeElement]): util.Collection[Group] = {
    if (isParentGrouped(parent)) return emptyList()

    val groups = mutable.HashMap.empty[Group, SuperTypeGroup]
    children.forEach {
      case child@Element.inheritedMember(member) =>
        val groupClass = member.containingClass
        if (groupClass != null) {
          val ownershipType = SuperTypeGroup.OwnershipType.INHERITS
          val group = getOrCreateGroup(groupClass, ownershipType, groups)
          group.addMethod(child)
        }
      case _ =>
    }

    groups.keySet.asJava
  }

  private def isParentGrouped(parent: AbstractTreeNode[_]): Boolean =
    Iterator.iterate(parent)(_.getParent)
      .takeWhile(_ != null)
      .exists(_.getValue.isInstanceOf[SuperTypeGroup])

  private def getOrCreateGroup(groupClass: PsiClass, ownershipType: SuperTypeGroup.OwnershipType,
                               groups: mutable.Map[Group, SuperTypeGroup]): SuperTypeGroup = {
    val superTypeGroup = new SuperTypeGroup(groupClass, ownershipType)
    groups.get(superTypeGroup) match
      case Some(existing) =>
        existing
      case _ =>
        groups.put(superTypeGroup, superTypeGroup)
        superTypeGroup
  }
}
