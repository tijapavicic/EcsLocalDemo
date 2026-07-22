```shell
find . -type d -name target -exec rm -rf {} + 2>/dev/null && echo "✓ All target folders removed"
```

```shell
pkill -f "java -jar" && sleep 2 && \
cd /Users/copor/IdeaProjects/EcsLocalDemo && \
java -jar ecs-application/target/ecs-application-1.0.0.jar \
  --spring.profiles.active=local-minio \
  --app.security.bearer-token-auth-enabled=false \
  2>&1 | grep -E "(Started|ERROR|WARNING)" | head -20 &
```



```shell
sleep 8 && echo "=== Testing Rate Limiting (3 requests) ===" && \
for i in 1 2 3; do
  echo "Request $i:"
  curl -s -w "Status: %{http_code}\n\n" -X POST http://localhost:8080/api/files/upload \
    -F "file=@pom.xml" 2>&1 | tail -2
done
```