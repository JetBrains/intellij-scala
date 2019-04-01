package org.jetbrains.plugins.scala.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

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

    public static boolean hasDerivativeForFileType(@NotNull VirtualFile file) {
        for (ScalaLanguageDerivative derivative : EP_NAME.getExtensionList()) {
            if (derivative.myFileType == file.getFileType()) return true;
        }

        return false;
    }
}
