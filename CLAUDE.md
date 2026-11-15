# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project direction (from AGENTS.md — mandatory)

- Development follows the course report `docs/B23DCCN752_Duong_Trong_Thang.docx`. Read [docs/requirements-reference.md](docs/requirements-reference.md) before changing anything; apply only **Part 1 (shared design)**, ignore Part 2 (individual part).
- The report is a reference spec, not a to-do list. Implement only what the user asks; direct user requests win over the document. requirements-reference.md records decisions already made where code intentionally differs from the report (e.g. timeout rule, fleet shape).
- Keep the 5 straight ship types (5/4/3/3/2) and current place/drag-drop interaction in Classic mode; do not switch to D4 cell-group placement (D4 shapes exist only in the separate Fleet Designer room mode).
- Keep the rule: 60 seconds without a valid shot = that player loses (not auto-random-shot).
- Reuse existing frontend/backend/shared code and protocol; don't rewrite working gameplay.
- Login is mandatory before Main Menu; never reintroduce Play Local. Don't change finalized visuals unless asked.
- README.md and docs are in Vietnamese; keep docs in Vietnamese when updating them. Feature reviews/verification notes live in `docs/*-review.md`.

## Commands

Java 25 toolchain, Gradle Wrapper 9.1.0 (macOS/Linux: `./gradlew`; Windows: `.\gradlew.bat`).

```bash
./gradlew build                      # compiles + runs all non-DB, non-OpenGL checks
./gradlew :backend:run               # TCP server on port 2209 (needs PostgreSQL); --args="2210" for another port
./gradlew :frontend:run -PplayerName="Player A" [-PserverHost=localhost -PserverPort=2209]
```

Database: `cp .env.example .env`, set `DB_PASSWORD`, then `docker compose up -d --wait` (PostgreSQL 17). Backend reads `DB_*` from `.env` in the working directory (Gradle tasks set it to repo root). Migrations are SQL files in `backend/src/main/resources/db/` and must also be added to the hardcoded list in `backend/.../database/Database.java#migrate`.

### Tests

There is **no JUnit**. Each "check" is a `main()` class under `src/test/java` (using a local `check(condition, message)` helper) wired as a `JavaExec` Gradle task; most are attached to `check` so `build` runs them. To run one:

```bash
./gradlew :shared:gameChecks          # also :shared:designerChecks, :shared:aiChecks
./gradlew :backend:checkRoomRules     # also checkMatchTimer, checkDesigner, checkPasswords
./gradlew :frontend:checkAiMatch      # also checkBoardCoordinates, checkSettings, checkUiFeedback, ...
```

New check classes need a matching task in that module's `build.gradle` (and `tasks.named('check') { dependsOn ... }` if it should run on build).

Opt-in categories (not run by plain `build`):
- **PostgreSQL/TCP**: `./gradlew build -PdatabaseChecks` (enables checkTcpMatch, checkDesignerTcp, checkRoomSettingsTcp). Leaderboard/friends/invite/chat checks need a fresh, dedicated DB: create it via `docker compose exec -T postgres psql -U battleship -d postgres -c "CREATE DATABASE battleship_chat_test_manual"`, then `./gradlew :backend:checkChat -PchatDatabase=battleship_chat_test_manual` (likewise `-PleaderboardDatabase` — name must start with `battleship_lb_test_` —, `-PfriendsDatabase`, `-PinviteDatabase`).
- **OpenGL rendering** (real jME window): `:frontend:checkGameplayRendering`, `checkMainMenu`, `checkAiGameplayRendering`, `checkDesignerRendering`, `checkCombatPresentation`, `checkUiInteractions`, `checkTypographyRendering`, `checkDisplayWindows`, `checkCursorFocus`.
- **Two desktop clients against a running backend** on localhost:2209 (`-PserverPort` to change): `checkPvpRooms`, `checkAccountScreens`, `checkPvpDisconnect`, `checkLeaderboardScreens`, `checkFriendsScreens`, `checkGameInviteScreens`, `checkChatScreens`, `checkSettingsScreens` — all drive `PvpRoomRenderChecks` with a different `battleship.*Only` system property.

## Architecture

Three Gradle modules: `shared` (pure Java + Gson, no jME), `backend` (TCP server + JDBC/PostgreSQL, headless), `frontend` (jMonkeyEngine 3.8 desktop client). Frontend and backend both depend on shared; backend never depends on frontend.

- **Game rules live in `shared.game.GameEngine`** (with `GameRules`, `TurnRule`, `ShipShapes`, `Board`, `Fleet`). The same engine is used by the server for PvP and by the client for AI Battle and placement UI. Room settings (Basic vs Fleet Designer, 3–5 ships taken in order Carrier→Battleship→Cruiser→Submarine→Destroyer, alternate vs hit-grants-another-shot) flow into `GameRules`.
- **Server is authoritative for PvP**: it owns secret fleets, turns, hit/miss, the turn deadline (`System.nanoTime()`, timeout and attacks handled under the same lock) and results. Ship sizes are derived from `ShipType` server-side; never trust client-sent sizes/cells. Clients only interpolate `TURN_TIMER` for display.
- **Protocol** (`shared.protocol`): `Message{type, requestId, payload}` JSON, framed as 4-byte big-endian length + UTF-8 JSON (max 65,536 bytes) by `MessageCodec`; all types in `MessageType`. Payload DTOs are records in `shared.room`/`match`/`auth`/`friends`/`chat`/`leaderboard`. Match snapshots carry a `revision`; clients drop stale/duplicate ones. `PING/PONG` heartbeat every 10s with read timeouts. Adding a message = add to `MessageType`, DTO in shared, dispatch in backend `ClientHandler`, handling in frontend `MessageHandler`/controller.
- **Backend flow**: `ServerMain` → `TcpServer` → one `ClientHandler` per socket (bounded per-connection send queue) with a `ClientSession`. `ClientHandler` switches on message type; auth/friends/leaderboard/invites are handled directly, while room and match messages (CREATE/JOIN/LEAVE_ROOM, READY, SUBMIT_FLEET, ATTACK, REMATCH_*, CHAT_SEND) require login and go to `RoomManager`, which drives match services. `ConnectionManager` tracks sessions for online status and pushes.
- **Frontend flow**: `BattleshipApplication` (jME `SimpleApplication`) + `ScreenController` handle screen navigation (login → Main Menu → AI Battle / PvP / Friends / Leaderboard / Settings / Profile). `network.NetworkClient` does socket I/O on its own threads and hands events to the render thread via `enqueue`; renderer/UI code never touches sockets directly. `game.GameController` drives placement/battle presentation for both PvP (`pvp.PvpController`) and AI (`ai.AiMatchController`, bots in `shared.ai`: Easy/Medium/Hard, given only public info, no room or DB history).
- Ship 3D models are built procedurally (`graphics/ship`) from the occupied cells, so designer-mode shapes render correctly; scene/environment, effects, camera and HUD are separate packages.
- Desktop settings (window/fullscreen, size, orbit/zoom sensitivity) are saved locally by `frontend.settings`.
- On macOS the frontend needs `-XstartOnFirstThread` (already set in `run` and OpenGL check tasks). On Windows, `:frontend:run` is replaced by `runAuto`/`runIntegrated` via a jlink'd private runtime and `scripts/run-game.ps1`.
