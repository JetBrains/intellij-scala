package org.jetbrains.plugins.scala.lang.lexer

import com.intellij.lexer.Lexer
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import junit.framework.Test
import junit.framework.TestCase
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.{Scala3Language, ScalaFileType}
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighterFactory
import org.jetbrains.plugins.scala.project.{ScalaLanguageLevel, UserDataKeys}

class Scala3HighlightingLexerTest extends TestCase

object Scala3HighlightingLexerTest {
  def suite: Test = new ScalaLexerTestBase("/lexer/highlighting3") {
    override protected def createLexer(project: Project): Lexer = {
      val module = ModuleManager.getInstance(project).getModules()(0)

      //to detect module settings, we need a non-null file corresponding to the file
      val virtualFile = new LightVirtualFile("dummy.scala", ScalaFileType.INSTANCE, "")

      module.putUserData(UserDataKeys.LightTestScalaVersion, ScalaLanguageLevel.Scala_3_3)
      virtualFile.putUserData(ScalaSyntaxHighlighterFactory.LightTestModuleKey, module)

      val scalaSyntaxHighlighter = ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter(project, virtualFile, getLanguage)
      scalaSyntaxHighlighter.getHighlightingLexer
    }

    override protected def getLanguage: Scala3Language = return Scala3Language.INSTANCE
  }
}
