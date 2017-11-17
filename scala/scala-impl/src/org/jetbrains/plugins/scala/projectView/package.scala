package org.jetbrains.plugins.scala

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.util.io.FileUtilRt
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}

import scala.collection.Seq

/**
  * @author Pavel Fatin
  */
package object projectView {
  type Node = AbstractTreeNode[_]

  object WorksheetFile {
    def unapply(file: ScalaFile): Boolean = file.isWorksheetFile
  }

  object ScriptFile {
    def unapply(file: ScalaFile): Boolean = file.isScriptFile
  }

  object ScalaDialectFile {
    def unapply(file: ScalaFile): Boolean = file.getFileType != ScalaFileType.INSTANCE
  }

  object SingularDefinition {
    def unapply(file: ScalaFile): Option[(ScTypeDefinition)] = Some(file.typeDefinitions) collect {
      case Seq(definition @ MatchesFileName()) => definition
      case Seq(definition) if definition.isPackageObject => definition
    }
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
      case Seq((definition1: ScTypeDefinition) && MatchesFileName(), definition2: ScTypeDefinition)
        if definition1.name == definition2.name => (definition1, definition2)
    }
  }

  private object MatchesFileName {
    def unapply(definition: ScTypeDefinition): Boolean =
      definition.containingFile.forall(file => FileUtilRt.getNameWithoutExtension(file.getName) == definition.name)
  }
}
