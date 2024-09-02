# IntelliJ GPT

For all the folks that want GPT Chat directly in the IDE, but don't want to add another monthly bill - here is a
IntelliJ Plugin to integrate it.

## Instructions

1) Download LM Studio https://lmstudio.ai/
2) Open it, Download your favorite, load it, and start the internal Server
3) Download this plugin
   1) open GPTChatWindow.kt 
   2) Replace the model class with the loaded in memory by your studio (You can see the value you need from http://localhost:1234/v1/models)  
   3) If you wish you could start the studio on some device in your local network (with enough RAM to be runnable) and change also the server constant
   4) and execute 'gradle assemble'
4) If build was successful a zip appears in build/distribution
5) Open IntelliJ -> Settings -> Plugins -> (The gear icon) -> Install from zip
6) After the plugin is loaded a GPT Chat Icon should appear on the right
