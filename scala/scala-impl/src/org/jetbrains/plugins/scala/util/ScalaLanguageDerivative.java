package org.jetbrains.plugins.scala.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaLanguage;
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile;

/**
 * User: Dmitry Naydanov
 * Date: 3/4/13
 */
abstract public class ScalaLanguageDerivative {

    public static final ExtensionPointName<ScalaLanguageDerivative> EP_NAME =
            ExtensionPointName.create("org.intellij.scala.scalaLanguageDerivative");

    private final FileType myFileType;

    protected ScalaLanguageDerivative(@NotNull FileType fileType) {
        myFileType = fileType;
    }

    private boolean isSuitableFor(@NotNull FileType fileType) {
        return myFileType == fileType;
    }

    private boolean isSuitableFor(@NotNull PsiFile file,
                                  @NotNull FileViewProvider viewProvider) {
        return isSuitableFor(file.getFileType()) &&
                viewProvider.getLanguages().contains(ScalaLanguage.INSTANCE);
    }

    public static boolean hasDerivativeForFileType(@NotNull VirtualFile file) {
        for (ScalaLanguageDerivative derivative : EP_NAME.getExtensionList()) {
            if (derivative.isSuitableFor(file.getFileType())) return true;
        }

        return false;
    }

    public static boolean hasDerivativeOnFile(@NotNull PsiFile file) {
        for (ScalaLanguageDerivative derivative : EP_NAME.getExtensions()) {
            if (derivative.isSuitableFor(file, file.getViewProvider())) return true;
        }

        return false;
    }

    @Nullable
    public static ScalaFile getScalaFileOnDerivative(PsiFile file) {
        for (ScalaLanguageDerivative derivative : EP_NAME.getExtensions()) {
            FileViewProvider viewProvider = file.getViewProvider();
            if (derivative.isSuitableFor(file, viewProvider)) {
                return (ScalaFile) viewProvider.getPsi(ScalaLanguage.INSTANCE);
            }
        }

        return null;
    }
}
