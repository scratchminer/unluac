# unluac
Fork of https://sourceforge.net/projects/unluac/ to work with the Playdate's Lua bytecode

## To decompile a Lua bytecode file:
(Note: you need the Java runtime to run the JAR file)

- Download the JAR from the releases section
- `java -jar unluac.jar -o (output_file.lua) (input_file.luac)`
- Alternately, you can use `unluac.sh` in this repo to work on a lot of files:

	`./unluac.sh (input directory) [optional output directory]`
	
	Passing the `-r` option before the input directory will delete the `luac` files after they have been decompiled.

## To build from source:
(Note: you need the Java Development Kit to build the JAR file)

- `git clone` the repo
- `cd` to its root directory
- `./build.sh` should create a `build` directory, stick the class files there, then put the finished JAR in the root directory
