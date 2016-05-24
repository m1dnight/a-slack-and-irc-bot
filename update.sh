#!/bin/bash

# Update from git
/usr/bin/git pull origin master

# Build a new uberjar
mv "$(lein uberjar | sed -n 's/^Created \(.*standalone\.jar\)/\1/p')" clojo.jar

# Set permissions
chown christophe:clojo clojo.jar

# Restart clojo
sudo stop clojo
sudo start clojo
