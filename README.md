# Chat Application

A privacy-first, real-time messaging backend built with Spring Boot, Native WebSocket, and MongoDB. The project focuses on secure, low-overhead message delivery with an opt-in connection model rather than open broadcast messaging.

> Status: Under active development. Core messaging and authentication flows are functional; some features listed below are still being implemented or hardened. Not production-ready yet.

## Overview

Most tutorial-style chat apps rely on STOMP over WebSocket and broadcast messages to every connected client. This project takes a different approach: it uses Native WebSockets directly, routes messages only to the intended recipients, and treats direct messaging as an explicit, consent-based interaction rather than an always-open channel.

## Features

- Real-time one-on-one and group messaging over Native WebSocket
- Opt-in direct messaging with an explicit connection request flow (Pending, Accepted, Rejected, Closed states) instead of unrestricted messaging
- Context-aware group moderation controls
- Session-bound authentication: WebSocket connections are tied to the authenticated HTTP session at handshake time, preventing sender spoofing
- Thread-safe, in-memory connection registry for routing messages only to active recipients (no broadcast-to-all)
- Event-driven background processing so message handling does not block HTTP request threads
- MongoDB schema designed to support both direct messages and group chats through a single, unified collection
- Distraction-free, minimal UI

## Tech Stack

- Backend: Java, Spring Boot, Spring Security, Spring MVC
- Real-time layer: Native WebSocket
- Database: MongoDB
- Build tool: Maven
- Frontend: Thymeleaf
- Api testing: Thunderclient

## Architecture Notes

- WebSocket handshake authentication is bound to the existing HTTP session (JSESSIONID) rather than trusting client-supplied identity fields, closing a common spoofing gap in naive WebSocket implementations.
- Active connections are tracked in a thread-safe map keyed by user identity, so messages are routed directly to the relevant session(s) instead of being broadcast to every open connection.
- Non-critical background work (such as notifications or side effects of a message being sent) is dispatched through Spring's application event system, keeping the main request/response cycle fast.

## Current Limitations / Work in Progress

- Group moderation rules are still being expanded
- Test coverage is partial; unit and integration tests are being added incrementally
- Deployment/CI pipeline is not yet set up
- API documentation is not yet published

## Running Locally

1. Clone the repository
2. Configure MongoDB connection details in `application.properties`
3. Build the project with Maven: `mvnw clean install`
4. Run the application: `mvnw spring-boot:run`

## Roadmap

- Finish group moderation feature set
- Add automated tests for core messaging and auth flows
- Add basic rate limiting on message sends
- Write API documentation

## License

Not yet decided.
