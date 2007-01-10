package org.jetbrains.plugins.scala;

import com.intellij.lang.*;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.highlighter.ScalaBraceMatcher;
import org.jetbrains.plugins.scala.highlighter.ScalaCommenter;
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter;
import org.jetbrains.plugins.scala.util.ScalaToolsFactory;
//import org.jetbrains.plugins.scala.lang.parser.ScalaParserDefinition;

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

  public FoldingBuilder getFoldingBuilder() {
    //return null;
    return ScalaToolsFactory.getInstance().createScalaFoldingBuilder();
  }

  public ParserDefinition getParserDefinition() {
    return ScalaToolsFactory.getInstance().createScalaParserDefinition();
  }

  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(Project project, final VirtualFile virtualFile) {
    return new ScalaSyntaxHighlighter();
  }

  @Nullable
  public PairedBraceMatcher getPairedBraceMatcher() {
    return new ScalaBraceMatcher();
  }

  @Nullable
  public Commenter getCommenter() {
    return new ScalaCommenter();
  }


  public FileViewProvider createViewProvider(final VirtualFile file, final PsiManager manager, final boolean physical) {
    return new SingleRootFileViewProvider(manager, file, physical) {
      @Nullable
      protected PsiFile getPsiInner(Language target) {
        if (target == StdLanguages.JAVA) return ScalaToolsFactory.getInstance().createJavaView(this);
        return super.getPsiInner(target);
      }
    };
  }
}
