#!/bin/sh

# unluac.sh
# A shell script to make unluac.jar easier to use. (Still requires the JRE, though)

if [ $# -eq 0 ]; then
	echo "Use this shell script to bulk-decompile Playdate Lua bytecode with unluac."
	echo "Usage: ./unluac.sh [-r] (input directory) [separate output directory]"
	echo
	echo "If '-r' is given, remove the input bytecode files after processing."
	echo "If there is no output directory given, create code in the input directory."
	echo
	exit
fi

if [ "$1" = "-r" ]; then
	REMOVE=1
else
	REMOVE=0
fi

if [ $REMOVE -eq 0 ]; then
	INPUTDIR="$1"
	OUTPUTDIR="$2"
else
	INPUTDIR="$2"
	OUTPUTDIR="$3"
fi

if [ -z "$OUTPUTDIR" ]; then OUTPUTDIR=$INPUTDIR; fi
IFS=$(printf '\n')
find "$INPUTDIR" ! -name "$(printf "*\n*")" -name '*.luac' > tmp
while IFS= read -r LUACFILE; do
	# Get the name of the new source file
	LUAFILE=$(echo "$LUACFILE" | sed "s+$INPUTDIR+$OUTPUTDIR+gI" | sed 's+.luac+.lua+gI')
    	echo "Decompiling $LUAFILE..."
	
	# If the folder containing it doesn't exist, create it
	[ ! -f "$LUAFILE" ] && mkdir -p "$(dirname "$LUAFILE")"
	
	# Run unluac
	java -jar "$(dirname "$0")/unluac.jar" -o "$LUAFILE" "$LUACFILE"
	
	# Remove the old bytecode file if '-r' was passed
	[ $REMOVE -eq 1 ] && rm -f "$LUACFILE"
done < tmp
rm tmp