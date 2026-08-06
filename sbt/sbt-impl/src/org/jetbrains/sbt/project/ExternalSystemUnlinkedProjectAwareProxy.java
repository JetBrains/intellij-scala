package org.jetbrains.sbt.project;

import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware;

/**
 * This class provides a proxy to the companion object of {@link ExternalSystemUnlinkedProjectAware},
 * which unfortunately cannot be directly referenced from Scala code.
 */
final class ExternalSystemUnlinkedProjectAwareProxy {
    private ExternalSystemUnlinkedProjectAwareProxy() {}

    static ExternalSystemUnlinkedProjectAware.Companion companion() {
        return ExternalSystemUnlinkedProjectAware.Companion;
    }
}
