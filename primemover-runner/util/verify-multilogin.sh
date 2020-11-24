#!/bin/bash
SERVICE="multilogin"
if pgrep -x "$SERVICE" >/dev/null
then
    echo "$SERVICE is running"
else
    echo "$SERVICE stopped -> restarting"
    # kill abandoned browser windows
    kill $(ps aux | grep '[m]ultiloginapp' | awk '{print $2}')
    # restart
    cd /opt/multilogin-5.5.4-101-linux_x86_64
    ./multilogin &
fi