#!/bin/bash

# Change this to your netid
netid=ktg150130

# Root directory of your project
PROJDIR=/home/010/k/kt/ktg150130/6378/Project2/Chandy-and-Lamports-Protocol/src

# Directory where the config file is located on your local system
CONFIGLOCAL=$HOME/Desktop/6378/config.txt

# Directory your java classes are in
BINDIR=$PROJDIR

# Your main project class
PROG=Program

n=0

cat $CONFIGLOCAL | sed -e "s/#.*//" | sed -e "/^\s*$/d" |
(
    read i
    i=$(echo $i | awk '{print $1;}' )
    echo $i
    while [ $n -lt $i ] 
    do
    	read line
    	n=$( echo $line | awk '{ print $1 }' )
        host=$( echo $line | awk '{ print $2 }' )

	echo $netid@$host java -cp $BINDIR $PROG $n
	
	lxterminal -e "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $netid@$host java -cp $BINDIR $PROG $n; $SHELL" &

        n=$(( n + 1 ))
    done
)
