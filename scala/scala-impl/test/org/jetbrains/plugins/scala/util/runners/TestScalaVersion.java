package org.jetbrains.plugins.scala.util.runners;

import org.jetbrains.plugins.scala.LatestScalaVersions$;

// required at compile time to use in annotations
public enum TestScalaVersion {

    Scala_2_10_0(false), Scala_2_10,
    Scala_2_11_0(false), Scala_2_11,
    Scala_2_12_0(false), Scala_2_12_12, Scala_2_12,
    Scala_2_13_0, Scala_2_13,
    Scala_3_0
    ;

    public final boolean supportsJdk11;

    TestScalaVersion(boolean supportsJdk11) {
        this.supportsJdk11 = supportsJdk11;
    }

    TestScalaVersion() {
        this(true);
    }

    public boolean supportsJdk(TestJdkVersion jdkVersion) {
        return jdkVersion != TestJdkVersion.JDK_11 || supportsJdk11;
    }

    public org.jetbrains.plugins.scala.ScalaVersion toProductionVersion() {
        switch (this) {
            case Scala_2_10: return LatestScalaVersions$.MODULE$.Scala_2_10();
            case Scala_2_11: return LatestScalaVersions$.MODULE$.Scala_2_11();
            case Scala_2_12: return LatestScalaVersions$.MODULE$.Scala_2_12();
            case Scala_2_12_12: return LatestScalaVersions$.MODULE$.Scala_2_12().withMinor(12);
            case Scala_2_13: return LatestScalaVersions$.MODULE$.Scala_2_13();
            case Scala_2_10_0: return LatestScalaVersions$.MODULE$.Scala_2_10().withMinor(0);
            case Scala_2_11_0: return LatestScalaVersions$.MODULE$.Scala_2_11().withMinor(0);
            case Scala_2_12_0: return LatestScalaVersions$.MODULE$.Scala_2_12().withMinor(0);
            case Scala_2_13_0: return LatestScalaVersions$.MODULE$.Scala_2_13().withMinor(0);
            case Scala_3_0: return LatestScalaVersions$.MODULE$.Scala_3_0();
            default: return null; // unreachable code
        }
    };
}
