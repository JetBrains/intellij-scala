package org.jetbrains.plugins.scala.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;

/**
 * User: Dmitry Naydanov
 * Date: 3/4/13
 */
abstract public class ScalaLanguageDerivative {
  public static final ExtensionPointName<ScalaLanguageDerivative> EP_NAME =
      ExtensionPointName.create("org.intellij.scala.scalaLanguageDerivative");
  
  public boolean isSuitableFile(PsiFile file) {
    return false;
  }
  
  @Nullable
  public ScalaFile getScalaFileIn(PsiFile file) {
    return null;
  }
  
  public boolean isSuitableForFileType(FileType fileType) {
    return false;
  }
  
  public static boolean hasDerivativeForFileType(FileType fileType) {
    for (ScalaLanguageDerivative derivative : EP_NAME.getExtensions()) {
      if (derivative.isSuitableForFileType(fileType)) return true;
    }
    
    return false;
  }
  
  public static boolean hasDerivativeOnFile(PsiFile file) {
    for (ScalaLanguageDerivative derivative : EP_NAME.getExtensions()) {
      if (derivative.isSuitableFile(file)) return true;
    }
    
    return false;
  }
  
  @Nullable
  public static ScalaFile getScalaFileOnDerivative(PsiFile file) {
    for (ScalaLanguageDerivative derivative : EP_NAME.getExtensions()) {
      if (derivative.isSuitableFile(file)) {
        return derivative.getScalaFileIn(file);
      }
    }
    
    return null;
  }
}
