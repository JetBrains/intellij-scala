package org.jetbrains.plugins.scala.lang.refactoring.rename.inplace;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.ExpressionContext;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.refactoring.rename.inplace.MyLookupExpression;
import org.jetbrains.plugins.scala.lang.refactoring.namesSuggester.ScalaNameSuggestionProvider;

import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * Nikolay.Tropin
 * 6/18/13
 */

//Mostly copy-pasted from com.intellij.refactoring.rename.inplace.MyLookupExpression,
//but this class uses only ScalaNameSuggestionProvider

public class ScalaLookupExpression extends MyLookupExpression {

  protected final LookupElement[] scalaLookupItems;

  public ScalaLookupExpression(final String name,
                               final LinkedHashSet<String> names,
                               final PsiNamedElement elementToRename,
                               final boolean shouldSelectAll,
                               final String advertisement) {
    super(name, names, elementToRename, shouldSelectAll, advertisement);
    scalaLookupItems = initLookupItems(names, elementToRename, shouldSelectAll);
  }

  private static LookupElement[] initLookupItems(LinkedHashSet<String> names,
                                                 PsiNamedElement elementToRename,
                                                 final boolean shouldSelectAll) {
    if (names == null) {
      names = new LinkedHashSet<String>();
      new ScalaNameSuggestionProvider().getSuggestedNames(elementToRename, elementToRename, names);
    }
    final LookupElement[] lookupElements = new LookupElement[names.size()];
    final Iterator<String> iterator = names.iterator();
    for (int i = 0; i < lookupElements.length; i++) {
      final String suggestion = iterator.next();
      lookupElements[i] = LookupElementBuilder.create(suggestion).withInsertHandler(new InsertHandler<LookupElement>() {
        public void handleInsert(InsertionContext context, LookupElement item) {
          if (shouldSelectAll) return;
          final Editor topLevelEditor = InjectedLanguageUtil.getTopLevelEditor(context.getEditor());
          final TemplateState templateState = TemplateManagerImpl.getTemplateState(topLevelEditor);
          if (templateState != null) {
            final TextRange range = templateState.getCurrentVariableRange();
            if (range != null) {
              topLevelEditor.getDocument().replaceString(range.getStartOffset(), range.getEndOffset(), suggestion);
            }
          }
        }
      });
    }
    return lookupElements;
  }

  public LookupElement[] calculateLookupItems(ExpressionContext context) {
    return scalaLookupItems;
  }

}
