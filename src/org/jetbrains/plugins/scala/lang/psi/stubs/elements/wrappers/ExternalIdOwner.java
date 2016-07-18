package org.jetbrains.plugins.scala.lang.psi.stubs.elements.wrappers;

import org.jetbrains.annotations.NotNull;

/**
 * @author adkozlov
 */
public interface ExternalIdOwner {
    @NotNull
    String getLanguageName();

    @NotNull
    String getExternalId();
}
