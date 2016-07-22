package org.jetbrains.plugins.dotty.lang.parser

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.parser.{PsiCreator, ScalaParserDefinition}

/**
  * @author adkozlov
  */
class DottyParserDefinition extends ScalaParserDefinition {
  override protected val psiCreator: PsiCreator = DottyPsiCreator

  override def createParser(project: Project): DottyParser = new DottyParser
}
