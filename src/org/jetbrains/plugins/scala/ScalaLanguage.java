/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
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
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.folding.FoldingBuilder;
import com.intellij.lang.folding.LanguageFolding;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.tree.TokenSet;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.codeInsight.hint.LanguageHintUtil;
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

  public ScalaLanguage() {
    super("Scala");
    LanguageFolding.INSTANCE.addExpicitExtension(this, ScalaToolsFactory.getInstance().createScalaFoldingBuilder());
    LanguageFindUsages.INSTANCE.addExpicitExtension(this, ScalaToolsFactory.getInstance().createFindUsagesProvider());
    LanguageParserDefinitions.INSTANCE.addExpicitExtension(this, ScalaToolsFactory.getInstance().createScalaParserDefinition());
    SyntaxHighlighterFactory.LANGUAGE_FACTORY.addExpicitExtension(this, new SyntaxHighlighterFactory() {
      @NotNull
      public SyntaxHighlighter getSyntaxHighlighter(Project project, VirtualFile file) {
        return new ScalaSyntaxHighlighter();
      }
    });
    LanguageBraceMatching.INSTANCE.addExpicitExtension(this, new ScalaBraceMatcher());
    LanguageFormatting.INSTANCE.addExpicitExtension(this, ScalaToolsFactory.getInstance().createScalaFormattingModelBuilder());
    LanguageStructureViewBuilder.INSTANCE.addExpicitExtension(this, new PsiStructureViewFactory() {
      @Nullable
      public StructureViewBuilder getStructureViewBuilder(PsiFile file) {
        return ScalaToolsFactory.getInstance().createStructureViewBuilder(file);
      }
    });
    LanguageCommenters.INSTANCE.addExpicitExtension(this, new ScalaCommenter());
    //LanguageSurrounders.INSTANCE.addExpicitExtension(this, ScalaToolsFactory.getInstance().createSurroundDescriptors().getSurroundDescriptors());
    LanguageFileViewProviders.INSTANCE.addExpicitExtension(this, new FileViewProviderFactory() {
      public FileViewProvider createFileViewProvider(VirtualFile file, Language language, PsiManager psiManager, boolean physical) {
        return new SingleRootFileViewProvider(psiManager, file, physical) {
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
    });
  }

}