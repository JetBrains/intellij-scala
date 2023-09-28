package org.jetbrains.plugins.scala.project;

import com.intellij.lang.Language;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.Scala3Language;
import org.jetbrains.plugins.scala.ScalaLanguage;
import scala.Option;
import scala.math.Ordered;

import java.util.Arrays;
import java.util.regex.Pattern;

public enum ScalaLanguageLevel implements Ordered<ScalaLanguageLevel> {

    Scala_2_9("2.9"),
    Scala_2_10("2.10"),
    Scala_2_11("2.11"),
    Scala_2_12("2.12"),
    Scala_2_13("2.13"),
    Scala_3_0("3.0"),
    Scala_3_1("3.1"),
    Scala_3_2("3.2"),
    //not yet released scala versions
    //(added in order Scala SDK is properly created for new major release candidate versions of the scala compiler)
    Scala_3_3("3.3"),
    Scala_3_4("3.4"),
    ;

    public boolean isScala3() {
        return this.compare(Scala_3_0) >= 0;
    }
    public boolean isScala2() {
        return this.compare(Scala_3_0) < 0;
    }

    public Language getLanguage() {
        return isScala3() ? Scala3Language.INSTANCE : ScalaLanguage.INSTANCE;
    }

    public static final ScalaLanguageLevel latestPublishedVersion = Scala_3_2;
    public static final ScalaLanguageLevel[] publishedVersions;

    static {
        publishedVersions = Arrays.stream(values()).takeWhile(x -> x.ordinal() <= latestPublishedVersion.ordinal()).toArray(ScalaLanguageLevel[]::new);
    }

    @NotNull
    private final String myVersion;
    @NotNull
    private final String myPattern;

    public static final Key<ScalaLanguageLevel> KEY = Key.create("SCALA_LANGUAGE_LEVEL");

    ScalaLanguageLevel(@NotNull String version) {
        this(version, Pattern.quote(version) + ".*");
    }

    ScalaLanguageLevel(@NotNull String version, @NotNull String pattern) {
        myVersion = version;
        myPattern = pattern;
    }

    @NotNull
    public String getVersion() {
        return myVersion;
    }

    @Override
    public int compare(@NotNull ScalaLanguageLevel that) {
        return super.compareTo(that);
    }

    @NotNull
    public static ScalaLanguageLevel getDefault() {
        return Scala_2_13;
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
