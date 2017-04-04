package org.jetbrains.plugins.scala

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}

import scala.collection.Seq

/**
  * @author Pavel Fatin
  */
package object projectView {
  type Node = AbstractTreeNode[_]

  object ScriptFile {
    def unapply(file: ScalaFile): Boolean = file.isScriptFile
  }

  object SingularDefinition {
    def unapply(file: ScalaFile): Option[(ScTypeDefinition)] = file.typeDefinitions match {
      case Seq(definition) if matchesFileName(definition) => Some(definition)
      case _ => None
    }

    private def matchesFileName(definition: ScTypeDefinition): Boolean =
      virtualFileOf(definition).forall(_.getNameWithoutExtension == definition.name)

    private def virtualFileOf(e: PsiElement): Option[VirtualFile] =
      Option(e.getContainingFile).flatMap(it => Option(it.getVirtualFile))
  }

  object PackageObject {
    def unapply(file: ScalaFile): Option[(ScTypeDefinition)] = file.typeDefinitions match {
      case Seq(definition) if definition.isPackageObject => Some(definition)
      case _ => None
    }
  }

  object ClassAndCompanionObject {
    def unapply(file: ScalaFile): Option[(ScClass, ScObject)] = file.typeDefinitions match {
      case Seq(classDefinition: ScClass, objectDefinition: ScObject)
        if classDefinition.name == objectDefinition.name => Some(classDefinition, objectDefinition)

      case Seq(objectDefinition: ScObject, classDefinition: ScClass)
        if classDefinition.name == objectDefinition.name => Some(classDefinition, objectDefinition)

      case _ => None
    }
  }

  object TraitAndCompanionObject {
    def unapply(file: ScalaFile): Option[(ScTrait, ScObject)] = file.typeDefinitions match {
      case Seq(traitDefinition: ScTrait, objectDefinition: ScObject)
        if traitDefinition.name == objectDefinition.name => Some(traitDefinition, objectDefinition)

      case Seq(objectDefinition: ScObject, traitDefinition: ScTrait)
        if traitDefinition.name == objectDefinition.name => Some(traitDefinition, objectDefinition)

      case _ => None
    }
  }
}
