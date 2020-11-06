package org.jetbrains.plugins.scala.project;

import org.jetbrains.annotations.NotNull;
import scala.Option;
import scala.math.Ordered;

import java.util.regex.Pattern;

public enum ScalaLanguageLevel implements Ordered<ScalaLanguageLevel>, Named {

    Scala_2_9("2.9"),
    Scala_2_10("2.10"),
    Scala_2_11("2.11"),
    Scala_2_12("2.12"),
    Scala_2_13("2.13"),
    Dotty("0.27"),
    Scala_3_0("3.0");

    @NotNull
    private final String myVersion;
    @NotNull
    private final String myName;
    @NotNull
    private final String myPattern;

    ScalaLanguageLevel(@NotNull String version) {
        this(version, version, Pattern.quote(version) + ".*");
    }

    ScalaLanguageLevel(@NotNull String version, @NotNull String name, @NotNull String pattern) {
        myVersion = version;
        myName = name;
        myPattern = pattern;
    }

    @NotNull
    public String getVersion() {
        return myVersion;
    }

    @NotNull
    @Override
    public String getName() {
        return myName;
    }

    @Override
    public int compare(@NotNull ScalaLanguageLevel that) {
        return super.compareTo(that);
    }

    @NotNull
    public static ScalaLanguageLevel getDefault() {
        return Scala_2_12;
    }

    @NotNull
    public static Option<ScalaLanguageLevel> findByVersion(@NotNull String version) {
        for (ScalaLanguageLevel languageLevel : values()) {
            if (version.matches(languageLevel.myPattern)) {
                return Option.apply(languageLevel);
            }
        }

        return Option.empty();
    }
}
