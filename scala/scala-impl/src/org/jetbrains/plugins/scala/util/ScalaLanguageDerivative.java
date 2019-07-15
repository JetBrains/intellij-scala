package org.jetbrains.plugins.scala.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;

/**
 * User: Dmitry Naydanov
 * Date: 3/4/13
 */
abstract public class ScalaLanguageDerivative {

    public static final ExtensionPointName<ScalaLanguageDerivative> EP_NAME =
            ExtensionPointName.create("org.intellij.scala.scalaLanguageDerivative");

    @NotNull
    private final FileType myFileType;

    protected ScalaLanguageDerivative(@NotNull FileType fileType) {
        myFileType = fileType;
    }

    public static boolean existsFor(@NotNull FileType fileType) {
        for (ScalaLanguageDerivative derivative : EP_NAME.getExtensionList()) {
            if (derivative.myFileType.equals(fileType)) return true;
        }

        return false;
    }
}
