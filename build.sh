clj -T:build clean
clj -T:build uber
cp target/*.jar ~/gitblog/publish.jar
chmod ~/gitblog/publish.jar +x