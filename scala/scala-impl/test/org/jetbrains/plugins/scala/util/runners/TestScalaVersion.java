package org.jetbrains.plugins.scala.util.runners;

import org.jetbrains.plugins.scala.Scala_2_10$;
import org.jetbrains.plugins.scala.Scala_2_11$;
import org.jetbrains.plugins.scala.Scala_2_12$;
import org.jetbrains.plugins.scala.Scala_2_13$;

// required at compile time to use in annotations
public enum TestScalaVersion {

    Scala_2_10,
    Scala_2_11,
    Scala_2_12,
    Scala_2_13;

    public org.jetbrains.plugins.scala.ScalaVersion toProductionVersion() {
        switch (this) {
            case Scala_2_10: return Scala_2_10$.MODULE$;
            case Scala_2_11: return Scala_2_11$.MODULE$;
            case Scala_2_12: return Scala_2_12$.MODULE$;
            case Scala_2_13: return Scala_2_13$.MODULE$;
            default: return null; // unreachable code
        }
    };
}
