package org.jetbrains.plugins.scala.cache.info;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.cache.info.impl.ScalaFileInfoImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.lang.StdLanguages;

/**
 * @author Ilya.Sergey
 */
public class ScalaInfoFactory {

  /**
   * Creates new ScalaFileInfo for given file
   *
   * @param project Current project
   * @param file    current file
   * @return ScalaFileInfo object containing information about file
   *         or null if file cannot be found or isn`t scala file
   */

  @Nullable
  public static ScalaFileInfo createScalaFileInfo(@NotNull final Project project, @NotNull final VirtualFile file) {
    if (!file.isValid()) {
      return null;
    }

    final PsiManager myPsiManager = PsiManager.getInstance(project);
    PsiFile psiFile = myPsiManager.findFile(file);
    FileViewProvider provider = psiFile.getViewProvider();
    PsiJavaFile javaPsi = (PsiJavaFile) provider.getPsi(StdLanguages.JAVA);
    PsiClass[] classes = javaPsi.getClasses();

    final String parentDirUrl = file.getParent() == null ? null : file.getParent().getUrl();
    if (parentDirUrl == null) {
      return null;
    }

    ScalaFileInfo fileInfo = new ScalaFileInfoImpl(file.getName(), file.getUrl() , parentDirUrl, file.getTimeStamp());
    fileInfo.setClasses(classes);

    return fileInfo;
  }


  /**
   * Creates new ScalaFileInfo for given file
   *
   * @param project Current project
   * @param file    current file
   * @return ScalaFileInfo object containing information about file
   *         or null if file cannot be found or isn`t scala file
   */
  public static  PsiJavaFile createPsiFile(@NotNull final Project project, @NotNull final VirtualFile file) {
    if (!file.isValid()) {
      return null;
    }

    final PsiManager myPsiManager = PsiManager.getInstance(project);
    PsiFile psiFile = myPsiManager.findFile(file);
    FileViewProvider provider = psiFile.getViewProvider();
    PsiJavaFile javaPsi = (PsiJavaFile) provider.getPsi(StdLanguages.JAVA);
    return javaPsi;
  }

}
