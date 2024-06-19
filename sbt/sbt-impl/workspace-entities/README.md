### Regenerate entities

To regenerate entity implementations, follow these steps:
- remove generated code inside [src directory](src) (`//region generated code`)
- remove [gen directory](gen)
- invoke `Generate implementation` intention on [SbtModuleEntity](src/com/intellij/entities/SbtModuleEntity.kt) interface
