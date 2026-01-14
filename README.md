# Live Transcription Spring Boot (stub)

This is a minimal Spring Boot WebSocket backend that implements a `/realtime` endpoint and a stub `TranscriptionService` for local development.

Run locally (requires Maven and JDK 17+):

```bash
cd server-springboot
mvn -q spring-boot:run
```

The server will listen on port 3001 by default (configured in `application.properties`).

Endpoints:
- WebSocket: `ws://localhost:3001/realtime` — accepts binary PCM16 frames and emits JSON messages to the client.

Next steps:
- Replace the stub in `TranscriptionServiceFactory` with a real adapter to AssemblyAI, Azure Speech, or other ASR provider.
- Implement authentication and upstream error mapping.
