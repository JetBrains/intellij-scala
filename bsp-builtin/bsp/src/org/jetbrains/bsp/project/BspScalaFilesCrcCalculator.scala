package org.jetbrains.bsp.project

import com.intellij.lang.{LanguageParserDefinitions, ParserDefinition}
import com.intellij.lexer.{LayeredLexer, Lexer}
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.AbstractCrcCalculator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.tree.IElementType
import org.jetbrains.bsp.BSP
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scalaDirective.ScalaDirectiveLanguage
import org.jetbrains.plugins.scalaDirective.lang.lexer.ScalaDirectiveElementType
import org.jetbrains.plugins.scalaDirective.lang.parser.ScalaDirectiveElementTypes

/**
 * The CRC calculator used for computing CRC values for scala affected external project files in the BSP external system.
 * In practice, it is only applicable to Scala CLI because only there the affected external project files are Scala files.
 */
class BspScalaFilesCrcCalculator extends AbstractCrcCalculator {

  override def isApplicable(projectSystemId: ProjectSystemId, virtualFile: VirtualFile): Boolean =
    projectSystemId == BSP.ProjectSystemId && virtualFile.getFileType == ScalaFileType.INSTANCE

  override def isIgnoredToken(iElementType: IElementType, charSequence: CharSequence, parserDefinition: ParserDefinition): Boolean =
    iElementType match {
      case _: ScalaDirectiveElementType => false
      case _ => true
    }

  override def createLexer(project: Project, parserDefinition: ParserDefinition): Lexer = {
    val scalaLexer = parserDefinition.createLexer(project)
    val scalaDirectiveParser = LanguageParserDefinitions.INSTANCE.forLanguage(ScalaDirectiveLanguage.INSTANCE)
    val scalaDirectiveLexer = scalaDirectiveParser.createLexer(project)
    new LayeredLexer(scalaLexer) {
      locally {
        registerSelfStoppingLayer(scalaDirectiveLexer, Array(ScalaDirectiveElementTypes.SCALA_DIRECTIVE), IElementType.EMPTY_ARRAY)
      }
    }
  }
}
