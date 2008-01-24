/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala;

import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.*;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.cacheBuilder.DefaultWordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.ide.structureView.StructureViewBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.highlighter.ScalaBraceMatcher;
import org.jetbrains.plugins.scala.highlighter.ScalaCommenter;
import org.jetbrains.plugins.scala.highlighter.ScalaSyntaxHighlighter;
import org.jetbrains.plugins.scala.util.ScalaToolsFactory;
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer;

import java.io.Serializable;

/**
 * Author: Ilya Sergey
 * Date: 20.09.2006
 * Time: 15:01:34
 */
public class ScalaLanguage extends Language {

  @Nullable
  public FoldingBuilder getFoldingBuilder() {
    return ScalaToolsFactory.getInstance().createScalaFoldingBuilder();
  }

  @NotNull
  public FindUsagesProvider getFindUsagesProvider() {
    return ScalaToolsFactory.getInstance().createFindUsagesProvider();
  }

  @Nullable
  public ParserDefinition getParserDefinition() {
    return ScalaToolsFactory.getInstance().createScalaParserDefinition();
  }

  @Nullable
  public FormattingModelBuilder getFormattingModelBuilder() {
    return ScalaToolsFactory.getInstance().createScalaFormattingModelBuilder();
  }

  @Nullable
  public PairedBraceMatcher getPairedBraceMatcher() {
    return new ScalaBraceMatcher();
  }

  @NotNull
  public SyntaxHighlighter getSyntaxHighlighter(Project project, VirtualFile virtualFile) {
    return new ScalaSyntaxHighlighter();
  }

  @Nullable
  public StructureViewBuilder getStructureViewBuilder(PsiFile file) {
    return ScalaToolsFactory.getInstance().createStructureViewBuilder(file);
  }

  @Nullable
  public Commenter getCommenter() {
    return new ScalaCommenter();
  }

  @NotNull
  public SurroundDescriptor[] getSurroundDescriptors() {
    return ScalaToolsFactory.getInstance().createSurroundDescriptors().getSurroundDescriptors();
  }

  @Nullable
  public FileViewProvider createViewProvider(VirtualFile file, PsiManager manager, boolean physical) {
    return new SingleRootFileViewProvider(manager, file, physical) {
      @Nullable
      protected PsiFile getPsiInner(Language target) {
        if (target == StdLanguages.JAVA) return myJavaRoot;
        return super.getPsiInner(target);
      }

      PsiFile myJavaRoot = ScalaToolsFactory.getInstance().createJavaView(this);

      public PsiElement findElementAt(int offset, Language language) {
        if (language == StdLanguages.JAVA) return myJavaRoot.findElementAt(offset);
        return super.findElementAt(offset, language);
      }
    };

  }

  public ScalaLanguage() {
    super("Scala");
  }

}