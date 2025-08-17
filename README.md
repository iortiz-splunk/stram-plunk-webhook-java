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
  -H 'X-Signature: c05113d360256587701a54fe01679059ee312599999ebabfb49d9b17d43a4e56' \
  -H 'X-Webhook-Id: test-webhook-id-1755390269304_6653' \
  -H 'X-Api-Key: y3m398snys6w' \
  -d '{"channel":{"id":"general","type":"messaging"},"created_at":"2025-08-17T00:24:29.304752Z","event":"message.new","message":{"id":"msg123","text":"Hello from my test webhook!","user":{"id":"test_user_1","name":"Test User"}},"type":"message.new","x_webhook_id":"test-webhook-id-1755390269304_6653"}'

