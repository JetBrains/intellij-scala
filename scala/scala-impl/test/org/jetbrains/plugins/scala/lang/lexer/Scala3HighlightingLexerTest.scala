package org.jetbrains.plugins.scala.lang.lexer

import com.intellij.lang.Language
import com.intellij.lexer.Lexer
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighterFactory
import org.jetbrains.plugins.scala.project.{ScalaFeaturePusher, ScalaFeatures}
import org.jetbrains.plugins.scala.{Scala3Language, ScalaFileType, ScalaVersion}

import java.nio.file.Path

class Scala3HighlightingLexerTest extends ScalaLexerTestBase {
  override protected def relativeTestDataPath: Path = Path.of("lexer", "highlighting3")

  override protected def language: Language = Scala3Language.INSTANCE

  override protected def createLexer: Lexer = {
    val virtualFile = new LightVirtualFile("dummy.scala", ScalaFileType.INSTANCE, "")

    ScalaFeaturePusher.setFeatures(virtualFile, ScalaFeatures.onlyByVersion(ScalaVersion.Latest.Scala_3))

    val scalaSyntaxHighlighter = ScalaSyntaxHighlighterFactory.createScalaSyntaxHighlighter(project, virtualFile, language)
    scalaSyntaxHighlighter.getHighlightingLexer
  }
}
