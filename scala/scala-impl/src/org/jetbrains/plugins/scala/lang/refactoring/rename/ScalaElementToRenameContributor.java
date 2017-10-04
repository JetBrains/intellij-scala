package org.jetbrains.plugins.scala.lang.refactoring.rename;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;

import java.util.Map;

/**
 * User: Dmitry Naydanov
 * Date: 5/29/13
 */
public class ScalaElementToRenameContributor {
  public static ExtensionPointName<ScalaElementToRenameContributor> EP_NAME = 
      ExtensionPointName.create("org.intellij.scala.scalaElementToRenameContributor");
  
  public void addElements(PsiElement original, String newName, Map<PsiElement, String> allRenames) { }
  
  public static void getAll(PsiElement original, String newName, Map<PsiElement, String> allRenames) {
    for (ScalaElementToRenameContributor contributor : EP_NAME.getExtensions()) {
      contributor.addElements(original, newName, allRenames);
    }
  }
}
