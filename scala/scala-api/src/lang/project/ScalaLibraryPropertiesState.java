package org.jetbrains.plugins.scala.project;

import com.intellij.util.ArrayUtil;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;

import java.util.Arrays;
import java.util.Objects;

public final class ScalaLibraryPropertiesState {

    // We have to rely on the Java's enumeration for serialization
    @Tag("language-level")
    private final ScalaLanguageLevel languageLevel;

    @Tag("compiler-classpath")
    @XCollection(
            elementName = "root",
            valueAttributeName = "url"
    )
    private final String[] compilerClasspath;

    @Tag("scaladoc-extra-classpath")
    @XCollection(
            elementName = "root",
            valueAttributeName = "url"
    )
    private final String[] scaladocExtraClasspath;

    public ScalaLibraryPropertiesState() {
        this(ScalaLanguageLevel.getDefault(), ArrayUtil.EMPTY_STRING_ARRAY, ArrayUtil.EMPTY_STRING_ARRAY);
    }

    public ScalaLibraryPropertiesState(ScalaLanguageLevel languageLevel,
                                       String[] compilerClasspath,
                                       String[] scaladocExtraClasspath) {
        this.languageLevel = languageLevel;
        this.compilerClasspath = compilerClasspath;
        this.scaladocExtraClasspath = scaladocExtraClasspath;
    }

    public ScalaLanguageLevel getLanguageLevel() {
        return languageLevel;
    }

    public String[] getCompilerClasspath() {
        return compilerClasspath;
    }

    public String[] getScaladocExtraClasspath() {
        return scaladocExtraClasspath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScalaLibraryPropertiesState that = (ScalaLibraryPropertiesState) o;
        return languageLevel == that.languageLevel &&
                Arrays.equals(compilerClasspath, that.compilerClasspath) &&
                Arrays.equals(scaladocExtraClasspath, that.scaladocExtraClasspath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(languageLevel, Arrays.hashCode(compilerClasspath), Arrays.hashCode(scaladocExtraClasspath));
    }
}