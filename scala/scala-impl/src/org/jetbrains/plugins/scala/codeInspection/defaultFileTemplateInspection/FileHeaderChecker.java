package org.jetbrains.plugins.scala.codeInspection.defaultFileTemplateInspection;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.defaultFileTemplateUsage.DefaultFileTemplateUsageInspection;
import com.intellij.codeInspection.defaultFileTemplateUsage.ReplaceWithFileTemplateFix;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.util.IncorrectOperationException;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Alexey
 */
//todo: this is a copy.
public class FileHeaderChecker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.defaultFileTemplateUsage.FileHeaderChecker");

  static ProblemDescriptor checkFileHeader(final PsiFile file, final InspectionManager manager, boolean onTheFly) {
    FileTemplateManager fileTemplateManager = FileTemplateManager.getInstance(file.getProject());
    FileTemplate template = fileTemplateManager.getDefaultTemplate(FileTemplateManager.FILE_HEADER_TEMPLATE_NAME);
    TIntObjectHashMap<String> offsetToProperty = new TIntObjectHashMap<String>();
    String templateText = template.getText().trim();
    String regex = templateToRegex(fileTemplateManager, templateText, offsetToProperty);
    regex = ".*("+regex+").*";
    String fileText = file.getText();
    Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
    Matcher matcher = pattern.matcher(fileText);
    if (matcher.matches()) {
      final int startOffset = matcher.start(1);
      final int endOffset = matcher.end(1);
      PsiElement leaf = file.findElementAt(startOffset);
      while (leaf != null && !leaf.getTextRange().containsRange(startOffset, endOffset)) leaf = leaf.getParent();
      PsiDocComment element = null;
      if (leaf instanceof PsiDocComment) element = (PsiDocComment) leaf;
      if (element == null) return null;
      LocalQuickFix[] quickFix = createQuickFix(element, matcher, offsetToProperty);
      final String description = InspectionsBundle.message("default.file.template.description");
      return manager.createProblemDescriptor(element, description, onTheFly, quickFix, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
    }
    return null;
  }

  private static Properties computeProperties(final Matcher matcher, final TIntObjectHashMap<String> offsetToProperty) {
    Properties properties = new Properties(FileTemplateManager.getDefaultInstance().getDefaultProperties());
    int[] offsets = offsetToProperty.keys();
    Arrays.sort(offsets);

    for (int i = 0; i < offsets.length; i++) {
      final int offset = offsets[i];
      String propName = offsetToProperty.get(offset);
      int groupNum = i + 2; // first group is whole doc comment
      String propValue = matcher.group(groupNum);
      properties.put(propName, propValue);
    }
    return properties;
  }

  private static LocalQuickFix[] createQuickFix(final PsiDocComment element,
                                              final Matcher matcher,
                                              final TIntObjectHashMap<String> offsetToProperty) {
    final FileTemplate template = FileTemplateManager.getInstance(element.getProject())
            .getPattern(FileTemplateManager.FILE_HEADER_TEMPLATE_NAME);
    final Runnable runnable = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            if (!element.isValid()) return;
            if (!CodeInsightUtil.preparePsiElementsForWrite(element)) return;
            String newText;
            try {
              newText = template.getText(computeProperties(matcher, offsetToProperty));
            }
            catch (IOException e) {
              LOG.error(e);
              return;
            }
            try {
              int offset = element.getTextRange().getStartOffset();
              PsiFile psiFile = element.getContainingFile();
              if (psiFile == null) return;
              PsiDocumentManager documentManager = PsiDocumentManager.getInstance(psiFile.getProject());
              Document document = documentManager.getDocument(psiFile);
              if (document == null) return;

              element.delete();
              documentManager.doPostponedOperationsAndUnblockDocument(document);
              documentManager.commitDocument(document);

              document.insertString(offset, newText);
            }
            catch (IncorrectOperationException e) {
              LOG.error(e);
            }
            catch (IllegalStateException e) {
              LOG.error("Cannot create doc comment from text: '" + newText + "'", e);
            }
          }
        });
      }
    };

    final ReplaceWithFileTemplateFix replaceTemplateFix = new ReplaceWithFileTemplateFix() {
      public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
        runnable.run();
      }
    };
    final LocalQuickFix editFileTemplateFix =
        DefaultFileTemplateUsageInspection.createEditFileTemplateFix(template, replaceTemplateFix);

    if (template.isDefault()) {
      return new LocalQuickFix[]{editFileTemplateFix};
    }
    return new LocalQuickFix[]{replaceTemplateFix,editFileTemplateFix};
  }

  private static String templateToRegex(FileTemplateManager fileTemplateManager, final String text, TIntObjectHashMap<String> offsetToProperty) {
    String regex = text;
    @NonNls Collection<String> properties = new ArrayList<String>((Collection) fileTemplateManager.getDefaultProperties().keySet());
    properties.add("PACKAGE_NAME");

    regex = escapeRegexChars(regex);
    // first group is a whole file header
    int groupNumber = 1;
    for (String name : properties) {
      String escaped = escapeRegexChars("${" + name + "}");
      boolean first = true;
      for (int i = regex.indexOf(escaped); i != -1 && i < regex.length(); i = regex.indexOf(escaped, i + 1)) {
        String replacement = first ? "(.*)" : "\\" + groupNumber;
        final int delta = escaped.length() - replacement.length();
        int[] offs = offsetToProperty.keys();
        for (int off : offs) {
          if (off > i) {
            String prop = offsetToProperty.remove(off);
            offsetToProperty.put(off - delta, prop);
          }
        }
        offsetToProperty.put(i, name);
        regex = regex.substring(0, i) + replacement + regex.substring(i + escaped.length());
        if (first) {
          groupNumber++;
          first = false;
        }
      }
    }
    return regex;
  }

  private static String escapeRegexChars(String regex) {
    regex = StringUtil.replace(regex,"|", "\\|");
    regex = StringUtil.replace(regex,".", "\\.");
    regex = StringUtil.replace(regex,"*", "\\*");
    regex = StringUtil.replace(regex,"+", "\\+");
    regex = StringUtil.replace(regex,"?", "\\?");
    regex = StringUtil.replace(regex,"$", "\\$");
    regex = StringUtil.replace(regex,"(", "\\(");
    regex = StringUtil.replace(regex,")", "\\)");
    regex = StringUtil.replace(regex,"[", "\\[");
    regex = StringUtil.replace(regex,"]", "\\]");
    regex = StringUtil.replace(regex,"{", "\\{");
    regex = StringUtil.replace(regex,"}", "\\}");
    return regex;
  }
}

