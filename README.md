Amazfit’s Zepp application requires “Notification read, reply, and control” permissions to monitor notifications and send them to your watch. It looks like Android removes this permission when the Zepp app is (manually) quit. As a result, you don’t get notifications on your watch.

I hacked this little project, which runs in the background and checks every 15 minutes if Amazfit Zepp is missing the “Notification read, reply, and control” permission. If it is missing it gives a notification on your phone which allows you to directly open the setting. Far from ideal but it is something.

This app will do all that for you automatically if you give Shizuku permission.

Hope Amazfit will fix this soon!
