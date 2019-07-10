package org.jetbrains.plugins.scala.finder;

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
    protected abstract FileType getFileType();

    public static boolean existsFor(@NotNull FileType fileType) {
        for (ScalaLanguageDerivative derivative : EP_NAME.getExtensionList()) {
            if (derivative.getFileType().equals(fileType)) return true;
        }

        return false;
    }
}
