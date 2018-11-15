package org.jetbrains.plugins.scala.components.libextensions;

import java.util.Objects;

public class ExtensionProps {
    String artifact;
    String urlOverride;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtensionProps that = (ExtensionProps) o;
        return artifact.equals(that.artifact) &&
                Objects.equals(urlOverride, that.urlOverride);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artifact, urlOverride);
    }
}
