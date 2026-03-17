rem Start databases                                                                                                                     Context
docker compose up -d resource-db song-db                                                                                              24,877 tokens
                                                                                                                                           12% used
rem Build and run resource-service (map external port to container port)                                                                $0.35 spent
docker build -t resource-service ./resource-service
docker run --name=resource-service --network mp3tracker_default -p 8081:8081 -e SPRING_PROFILES_ACTIVE=docker -d resource-service

rem Build and run song-service
docker build -t song-service ./song-service
docker run --name=song-service --network mp3tracker_default -p 8082:8082 -e SPRING_PROFILES_ACTIVE=docker -d song-service