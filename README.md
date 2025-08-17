# stram-plunk-webhook-java

# Build the project
mvn clean install

# Run Docker Compose
docker compose up --build --force-recreate -d

docker ps -a

## Down
docker compose down 

## Test Locally
curl -X POST \
  http://localhost:8000/webhook \
  -H 'Content-Type: application/json' \
  -H 'X-Signature: 72725c971ffbb3ff06cfbcba015009fba035e1743954ed72c44db934405b759f' \
  -H 'X-Webhook-Id: test-webhook-id-1755459745575_9572' \
  -H 'X-Api-Key: y3m398snys6w' \
  -d '{"channel":{"id":"general","type":"messaging"},"created_at":"2025-08-17T19:42:25.576116Z","event":"message.new","message":{"id":"msg123","text":"Hello from my test webhook!","user":{"id":"test_user_1","name":"Test User"}},"type":"message.new","x_webhook_id":"test-webhook-id-1755459745575_9572"}'

