package org.jetbrains.plugins.scala.finder;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;

/**
 * User: Dmitry Naydanov
 * Date: 3/4/13
 */
abstract public class ScalaLanguageDerivative {

    private static final ExtensionPointName<ScalaLanguageDerivative> EP_NAME =
            ExtensionPointName.create("org.intellij.scala.scalaLanguageDerivative");

    @NotNull
    protected abstract LanguageFileType getFileType();

    public static boolean existsFor(@NotNull LanguageFileType fileType) {
        return EP_NAME.getExtensionList()
                .stream()
                .map(ScalaLanguageDerivative::getFileType)
                .anyMatch(fileType::equals);
    }
}
