#!/usr/bin/env bash

PORTS=(5000 5001 5002 5003 5004 5005)

for port in "${PORTS[@]}"; do
    gnome-terminal --title="Server on port $port" -- bash -c "java ServerMain.java $port; read"
done
