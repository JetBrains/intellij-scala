package org.jetbrains.plugins.scala.lang.refactoring.move;

import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.ide.fileTemplates.FileTemplateUtil;
import com.intellij.ide.fileTemplates.ui.CreateFromTemplateDialog;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaFileType;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;

import java.util.Properties;

/**
 * Pavel Fatin
 */
public class ScalaDirectoryService {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.scala.lang.refactoring.move.ScalaDirectoryService");

  static PsiClass createClassFromTemplate(@NotNull PsiDirectory dir,
                                          String name,
                                          String templateName,
                                          boolean askToDefineVariables) throws IncorrectOperationException {
    //checkCreateClassOrInterface(dir, name);

    FileTemplate template = FileTemplateManager.getInstance().getInternalTemplate(templateName);

    Properties defaultProperties = FileTemplateManager.getInstance().getDefaultProperties();
    Properties properties = new Properties(defaultProperties);
    properties.setProperty(FileTemplate.ATTRIBUTE_NAME, name);

    String ext = ScalaFileType.DEFAULT_EXTENSION;
    String fileName = name + "." + ext;

    PsiElement element;
    try {
      element = askToDefineVariables ? new CreateFromTemplateDialog(dir.getProject(), dir, template, null, properties).create()
          : FileTemplateUtil.createFromTemplate(template, fileName, properties, dir);
    } catch (IncorrectOperationException e) {
      throw e;
    } catch (Exception e) {
      LOG.error(e);
      return null;
    }
    if (element == null) return null;
    final ScalaFile file = (ScalaFile) element.getContainingFile();
    PsiClass[] classes = file.typeDefinitionsArray();
    if (classes.length < 1) {
      throw new IncorrectOperationException(getIncorrectTemplateMessage(templateName));
    }
    return classes[0];
  }

  private static String getIncorrectTemplateMessage(String templateName) {
    return PsiBundle.message("psi.error.incorroect.class.template.message",
        FileTemplateManager.getInstance().internalTemplateToSubject(templateName), templateName);
  }
}
