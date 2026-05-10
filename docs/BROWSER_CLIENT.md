# BROWSER_CLIENT.md

# Web Client Architecture & Build Guide

## Overview

This document defines the architecture, technology choices, rendering model, project structure, and implementation strategy for the web client of the multiplayer turn-based strategy game.

The backend already exists in Java and acts as the authoritative multiplayer server.
There is a frontend desktop application as an example which contains examples of sprite rendering, UI theme, level editor design etc.
All the required assets are provided in /public/assets

The web client will provide:

- Authentication
- Matchmaking
- Lobby system
- Game rendering
- Real-time multiplayer communication
- Level editor
- Replay system
- Community level sharing
- UI/UX systems
- Settings and account management

---

# Core Technology Stack

## Frontend Framework

- Next.js
- TypeScript
- TailwindCSS

## Rendering Engine

- PixiJS

## State Management

- Zustand

## Networking

- WebSockets

## Backend

- Existing Java authoritative game server

---

# Rendering Philosophy

## IMPORTANT

The game board MUST NOT be rendered using:
- HTML grids
- CSS transforms
- DOM nodes for tiles
- React component trees for entities

The game MUST render through:
- a single GPU-accelerated WebGL canvas
- managed by PixiJS

---

# Why PixiJS

PixiJS provides:

- WebGL rendering
- GPU batching
- Sprite rendering
- Tilemap support
- Texture atlas support
- Filters and shaders
- Camera systems
- Smooth zooming/panning
- Excellent TypeScript support
- High performance for large tile-based worlds

PixiJS internally uses WebGL.

Raw WebGL should NOT be used directly unless custom low-level rendering becomes necessary later.

---

# High-Level Architecture

```text
Next.js Application
│
├── React UI Layer
│   ├── Auth
│   ├── Matchmaking
│   ├── Lobby
│   ├── Menus
│   ├── Chat
│   ├── Settings
│   ├── Replay Browser
│   └── Level Browser
│
├── PixiJS Rendering Layer
│   ├── Tilemap Renderer
│   ├── Unit Renderer
│   ├── Camera System
│   ├── Effects
│   ├── Animations
│   └── Selection/UI Overlays
│
├── Networking Layer
│   ├── WebSocket Client
│   ├── Match Sync
│   ├── Turn Actions
│   ├── Reconnect Logic
│   └── Replay Serialization
│
└── State Layer
    ├── Game State
    ├── UI State
    ├── Session State
    └── Editor State