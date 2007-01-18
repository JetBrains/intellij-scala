package org.jetbrains.plugins.scala;

import com.intellij.lang.*;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.formatting.FormattingModelBuilder;
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
  public FormattingModelBuilder getFormattingModelBuilder() {
       return ScalaToolsFactory.getInstance().createScalaFormattingModelBuilder();
   }

//  @NotNull
//  public StructureViewBuilder getStructureViewBuilder(@NotNull final PsiFile psiFile) {
//      return ScalaToolsFactory.getInstance().createStructureViewBuilder(psiFile);
//  }

  @Nullable
  public Commenter getCommenter() {
    return new ScalaCommenter();
  }

  @NotNull
  public SurroundDescriptor[] getSurroundDescriptors() {
    return ScalaToolsFactory.getInstance().createSurroundDescriptors().getSurroundDescriptors();
  }

  public FileViewProvider createViewProvider(final VirtualFile file, final PsiManager manager, final boolean physical) {
    return new SingleRootFileViewProvider(manager, file, physical) {
      PsiFile myJavaRoot = ScalaToolsFactory.getInstance().createJavaView(this);

      public PsiElement findElementAt(int offset, Language language) {
        if (language == StdLanguages.JAVA) return myJavaRoot.findElementAt(offset);
        return super.findElementAt(offset, language);
      }
    };
  }
}