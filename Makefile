.PHONY: up down build logs clean proto-python proto-java test-backend

up:
	docker-compose up --build

up-d:
	docker-compose up --build -d

down:
	docker-compose down

logs:
	docker-compose logs -f

clean:
	docker-compose down -v --remove-orphans

# Regenerate Python gRPC stubs from proto/speech.proto into ai-worker/app/generated
proto-python:
	python3 -m grpc_tools.protoc \
		-I proto \
		--python_out=ai-worker/app/generated \
		--grpc_python_out=ai-worker/app/generated \
		proto/speech.proto
	sed -i 's/^import speech_pb2/from . import speech_pb2/' ai-worker/app/generated/speech_pb2_grpc.py

# Java stubs are generated automatically by the protobuf-maven-plugin during `mvn package`
proto-java:
	cd backend && mvn generate-sources

test-backend:
	cd backend && mvn test

dev-frontend:
	cd frontend && npm install --legacy-peer-deps && npm run dev

dev-backend:
	cd backend && mvn spring-boot:run

dev-ai-worker:
	cd ai-worker && pip install -r requirements.txt --break-system-packages && python -m app.main
