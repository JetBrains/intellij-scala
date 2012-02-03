package org.jetbrains.plugins.scala.lang.resolve;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings;
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait;
import org.jetbrains.plugins.scala.util.TestUtils;

/**
 * User: Alexander Podkhalyuzin
 * Date: 01.11.11
 */
public class ResolveClassLocTest extends ScalaResolveTestCase {
  public String folderPath() {
    return super.folderPath() + "resolve/class/loc/";
  }

  public void testMyClass() throws Exception {
    try {
      CodeStyleSettingsManager.getInstance(getProjectAdapter()).getCurrentSettings().
          getCustomSettings(ScalaCodeStyleSettings.class).IGNORE_PERFORMANCE_TO_FIND_ALL_CLASS_NAMES = true;
      PsiReference ref = findReferenceAtCaret();
      PsiElement resolved = ref.resolve();
      assertTrue(resolved instanceof ScTrait);
      assertEquals(((ScTrait) resolved).qualifiedName(), "org.MyTrait");
    }
    finally {
      CodeStyleSettingsManager.getInstance(getProjectAdapter()).getCurrentSettings().
          getCustomSettings(ScalaCodeStyleSettings.class).IGNORE_PERFORMANCE_TO_FIND_ALL_CLASS_NAMES = false;
    }
  }
}
