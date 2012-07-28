package org.jetbrains.plugins.scala.lang.completion

import com.intellij.util.ProcessingContext
import com.intellij.codeInsight.completion._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTrait, ScObject, ScClass, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import com.intellij.codeInsight.lookup.LookupElementBuilder
import collection.mutable


/**
 * @author Alefas
 * @since 31.03.12
 */
class ScalaMemberNameCompletionContributor extends CompletionContributor {
  //suggest class name
  extend(CompletionType.BASIC, ScalaSmartCompletionContributor.superParentsPattern(classOf[ScTypeDefinition]),
    new CompletionProvider[CompletionParameters]() {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val position = parameters.getPosition
        val fileName = parameters.getOriginalFile.getVirtualFile.getNameWithoutExtension
        val classesNames: mutable.HashSet[String] = mutable.HashSet.empty
        val objectNames: mutable.HashSet[String] = mutable.HashSet.empty
        val parent = position.getParent.getParent
        if (parent == null) return
        parent.getChildren.foreach {
          case c: ScClass => classesNames += c.name
          case t: ScTrait => classesNames += t.name
          case o: ScObject => objectNames += o.name
          case _ =>
        }
        val shouldCompleteFileName = parent match {
          case f: ScalaFile => true
          case p: ScPackaging => true
          case _ => false
        }
        if (shouldCompleteFileName && !classesNames.contains(fileName) && !objectNames.contains(fileName)) {
          result.addElement(LookupElementBuilder.create(fileName))
        }
        position.getParent match {
          case _: ScClass | _: ScTrait =>
            for (o <- objectNames if !classesNames.contains(o)) {
              result.addElement(LookupElementBuilder.create(o))
            }
          case o: ScObject =>
            for (o <- classesNames if !objectNames.contains(o)) {
              result.addElement(LookupElementBuilder.create(o))
            }
          case _ =>
        }
      }
    })
}
