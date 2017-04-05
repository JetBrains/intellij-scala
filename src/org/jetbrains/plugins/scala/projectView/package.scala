package org.jetbrains.plugins.scala

import com.intellij.ide.util.treeView.AbstractTreeNode
import org.jetbrains.plugins.scala.extensions._
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
    def unapply(file: ScalaFile): Option[(ScTypeDefinition)] = Some(file.typeDefinitions) collect {
      case Seq(definition) if matchesFileName(definition) => definition
      case Seq(definition) if definition.isPackageObject => definition
    }

    private def matchesFileName(definition: ScTypeDefinition): Boolean =
      definition.containingVirtualFile.forall(_.getNameWithoutExtension == definition.name)
  }

  object ClassAndCompanionObject {
    def unapply(file: ScalaFile): Option[(ScClass, ScObject)] = Some(file.typeDefinitions) collect {
      case PairedTypeDefinitions(aClass: ScClass, anObject: ScObject) => (aClass, anObject)
      case PairedTypeDefinitions(anObject: ScObject, aClass: ScClass) => (aClass, anObject)
    }
  }

  object TraitAndCompanionObject {
    def unapply(file: ScalaFile): Option[(ScTrait, ScObject)] = Some(file.typeDefinitions) collect {
      case PairedTypeDefinitions(aTrait: ScTrait, anObject: ScObject) => (aTrait, anObject)
      case PairedTypeDefinitions(anObject: ScObject, aTrait: ScTrait) => (aTrait, anObject)
    }
  }

  private object PairedTypeDefinitions {
    def unapply(definitions: Seq[ScTypeDefinition]): Option[(ScTypeDefinition, ScTypeDefinition)] = Some(definitions) collect {
      case Seq(definition1: ScTypeDefinition, definition2: ScTypeDefinition) if definition1.name == definition2.name =>
        (definition1, definition2)
    }
  }
}
