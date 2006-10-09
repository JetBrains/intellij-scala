package org.jetbrains.plugins.scala;

import com.intellij.lang.Language;
import com.intellij.lang.PairedBraceMatcher;
import com.intellij.lang.Commenter;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter;
import org.jetbrains.plugins.scala.highlighter.ScalaBraceMatcher;
import org.jetbrains.plugins.scala.highlighter.ScalaCommenter;
import org.jetbrains.plugins.scala.util.ScalaParserDefinitionFactory;

/**
 * Author: Ilya Sergey
 * Date: 20.09.2006
 * Time: 15:01:34
 */
public class ScalaLanguage extends Language {
    protected ScalaLanguage(String s) {
        super(s);
    }

    protected ScalaLanguage(String s, String... strings) {
        super(s, strings);
    }

    public ScalaLanguage() {
        super("Scala");
    }

    public ParserDefinition getParserDefinition(){
        return ScalaParserDefinitionFactory.getInstance().createScalaParserDefinition();
//        return null;

    }

    @NotNull
    public SyntaxHighlighter getSyntaxHighlighter(Project project, final VirtualFile virtualFile) {
        return new ScalaSyntaxHighlighter();
    }

    @Nullable
    public PairedBraceMatcher getPairedBraceMatcher(){
        return new ScalaBraceMatcher();
    }

    @Nullable
    public Commenter getCommenter(){
        return new ScalaCommenter();
    }
}
