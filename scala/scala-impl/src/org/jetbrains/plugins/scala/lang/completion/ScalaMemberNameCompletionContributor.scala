package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}

import scala.collection.mutable

/**
 * @author Alefas
 * @since 31.03.12
 */
class ScalaMemberNameCompletionContributor extends ScalaCompletionContributor {
  //suggest class name
  extend(CompletionType.BASIC, identifierWithParentPattern(classOf[ScTypeDefinition]),
    new CompletionProvider[CompletionParameters]() {
      override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
        val position = positionFromParameters(parameters)
        val classesNames: mutable.HashSet[String] = mutable.HashSet.empty
        val objectNames: mutable.HashSet[String] = mutable.HashSet.empty
        val parent = position.getContext.getContext
        if (parent == null) return
        parent.getChildren.foreach {
          case c: ScClass => classesNames += c.name
          case t: ScTrait => classesNames += t.name
          case o: ScObject => objectNames += o.name
          case _ =>
        }
        val shouldCompleteFileName = parent match {
          case _: ScalaFile => true
          case _: ScPackaging => true
          case _ => false
        }
        parameters.getOriginalFile.getVirtualFile match {
          case vFile: VirtualFile if shouldCompleteFileName =>
            val fileName = vFile.getNameWithoutExtension
            if (!classesNames.contains(fileName) && !objectNames.contains(fileName)) {
              result.addElement(LookupElementBuilder.create(fileName))
            }
          case _ =>
        }
        position.getContext match {
          case _: ScClass | _: ScTrait =>
            for (o <- objectNames if !classesNames.contains(o)) {
              result.addElement(LookupElementBuilder.create(o))
            }
          case _: ScObject =>
            for (o <- classesNames if !objectNames.contains(o)) {
              result.addElement(LookupElementBuilder.create(o))
            }
          case _ =>
        }
      }
    })
}
