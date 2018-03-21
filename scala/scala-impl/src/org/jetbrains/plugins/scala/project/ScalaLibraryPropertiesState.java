package org.jetbrains.plugins.scala.project;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.plugins.scala.project.Platform.Dotty$;
import org.jetbrains.plugins.scala.project.Platform.Scala$;
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.*;

import java.util.Arrays;
import java.util.Objects;

import static com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY;

/**
 * @author Pavel Fatin
 */
public class ScalaLibraryPropertiesState {
    // We have to rely on the Java's enumeration for serialization
    private final PlatformProxy platformProxy;

    // We have to rely on the Java's enumeration for serialization
    private final ScalaLanguageLevelProxy languageLevelProxy;

    public ScalaLibraryPropertiesState() {
        this(ScalaLanguageLevel.Default(), EMPTY_STRING_ARRAY);
    }

    public ScalaLibraryPropertiesState(ScalaLanguageLevel languageLevel, String[] compilerClasspath) {
        this(Scala$.MODULE$, languageLevel, compilerClasspath);
    }

    public ScalaLibraryPropertiesState(Platform platform, ScalaLanguageLevel languageLevel, String[] compilerClasspath) {
        platformProxy = PlatformProxy.asProxy(platform);
        languageLevelProxy = ScalaLanguageLevelProxy.asProxy(languageLevel);
        this.compilerClasspath = compilerClasspath;
    }

    public Platform getPlatform() {
        return platformProxy.getPlatform();
    }

    public ScalaLanguageLevel getLanguageLevel() {
        return languageLevelProxy.getLanguageLevel();
    }

    @Tag("compiler-classpath")
    @AbstractCollection(surroundWithTag = false, elementTag = "root", elementValueAttribute = "url")
    public final String[] compilerClasspath;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ScalaLibraryPropertiesState that = (ScalaLibraryPropertiesState) o;
        return platformProxy == that.platformProxy &&
                languageLevelProxy == that.languageLevelProxy &&
                Arrays.equals(compilerClasspath, that.compilerClasspath);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(platformProxy, languageLevelProxy);
        result = 31 * result + Arrays.hashCode(compilerClasspath);
        return result;
    }

    private enum PlatformProxy {
        Scala {
            @Override
            public Platform getPlatform() {
                return Scala$.MODULE$;
            }
        },
        Dotty {
            @Override
            public Platform getPlatform() {
                return Dotty$.MODULE$;
            }
        };

        abstract Platform getPlatform();

        static PlatformProxy asProxy(Platform platform) {
            if (platform instanceof Scala$) {
                return Scala;
            } else if (platform instanceof Dotty$) {
                return Dotty;
            } else {
                return null;
            }
        }
    }

    private enum ScalaLanguageLevelProxy {
        Snapshot {
            @Override
            ScalaLanguageLevel getLanguageLevel() {
                return Snapshot$.MODULE$;
            }
        },
        Scala_2_8 {
            @Override
            ScalaLanguageLevel getLanguageLevel() {
                return Scala_2_8$.MODULE$;
            }
        },
        Scala_2_9 {
            @Override
            ScalaLanguageLevel getLanguageLevel() {
                return Scala_2_9$.MODULE$;
            }
        },
        Scala_2_10 {
            @Override
            ScalaLanguageLevel getLanguageLevel() {
                return Scala_2_10$.MODULE$;
            }
        },
        Scala_2_11 {
            @Override
            ScalaLanguageLevel getLanguageLevel() {
                return Scala_2_11$.MODULE$;
            }
        },
        Scala_2_12 {
            @Override
            ScalaLanguageLevel getLanguageLevel() {
                return Scala_2_12$.MODULE$;
            }
        };

        abstract ScalaLanguageLevel getLanguageLevel();

        static ScalaLanguageLevelProxy asProxy(ScalaLanguageLevel languageLevel) {
            if (languageLevel instanceof Snapshot$) {
                return Snapshot;
            } else if (languageLevel instanceof Scala_2_8$) {
                return Scala_2_8;
            } else if (languageLevel instanceof Scala_2_9$) {
                return Scala_2_9;
            } else if (languageLevel instanceof Scala_2_10$) {
                return Scala_2_10;
            } else if (languageLevel instanceof Scala_2_11$) {
                return Scala_2_11;
            } else if (languageLevel instanceof Scala_2_12$) {
                return Scala_2_12;
            } else {
                return null;
            }
        }
    }
}