package org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.markdown;

import com.intellij.lang.Language;
import com.intellij.markdown.utils.lang.HtmlSyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;

public class HtmlSyntaxHighlighterCompanionProxy {
    public static HtmlChunk colorHtmlChunk(Project project, Language language, String rawContent) {
        return HtmlSyntaxHighlighter.Companion.colorHtmlChunk(project, language, rawContent);
    }
}
