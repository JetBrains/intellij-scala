# Generated sources

Contains "manually generated" sources that are created by an action in Grammar-Kit and committed
instead of being built by the regular build process from the actual bnf source.

## Usage

1. install Grammar-Kit plugin
2. set it up to use jflex-1.6.1.jar in your .ivy2 (it is downloaded as dependency of the build)
3. use on bnf files as instructed in Grammar-Kit documentation
4. move the generated files from `gen` to `src`

### TODO

Generate the files as part of the build via sbt. This has caused trouble because of conflicting 
classpaths with another plugin, so it's a semi-manual process for now, 
but can probably solved by doing it in separate process if necessary.
