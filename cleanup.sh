#!/bin/bash


# Change this to your netid
netid=ktg150130

#
# Root directory of your project
PROJDIR=/home/010/k/kt/ktg150130/6378

#
# Directory where the config file is located on your local system
CONFIGLOCAL=$HOME/Desktop/6378/config.txt

n=0

cat $CONFIGLOCAL | sed -e "s/#.*//" | sed -e "/^\s*$/d" |
(
    read i
    i=$(echo $i | awk '{ print $1 }' )
    echo $i
    while [ $n -lt $i ]
    do
    	read line
	n=$( echo $line | awk '{ print $1 }' )
        host=$( echo $line | awk '{ print $2 }' )

        echo $host
        lxterminal -e "ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no $netid@$host killall -u $netid" &
        sleep 1

        n=$(( n + 1 ))
    done
   
)


echo "Cleanup complete"
