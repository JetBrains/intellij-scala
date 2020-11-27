package org.jetbrains.plugins.scala.highlighter;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

@ApiStatus.Internal
public interface ScalaColorsAndFontsPageDescriptorsProvider {

    List<AttributesDescriptor> descriptors();

    ExtensionPointName<ScalaColorsAndFontsPageDescriptorsProvider> EP_NAME =
            ExtensionPointName.create("org.intellij.scala.colorsAndFontsPageDescriptorsProvider");
}
