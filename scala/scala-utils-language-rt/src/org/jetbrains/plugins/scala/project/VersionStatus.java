package org.jetbrains.plugins.scala.project;

enum VersionStatus implements Comparable<VersionStatus> {
    OTHER, // ~2.0.0-alpha123, 2.0.0-beta123, 2.0.0-qwerty
    MILESTONE, // ~2.0.0-M3
    RC, // ~2.0.0-RC2
    DEFAULT // ~2.0.0
}
