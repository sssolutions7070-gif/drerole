# DreamRole

A hiring platform that goes beyond job listings: candidates get matched to jobs from their resume, and recruiters can run **proctored aptitude and role-specific tests**, shortlist candidates (including people who never registered), and schedule interviews, all in one place.

## Features

- **Resume-based job and internship recommendations** using skill extraction and fuzzy skill matching (only jobs at or above a 50% match are shown).
- **Assessment pipeline for recruiters:** manual aptitude tests and "DreamRole" tests (40 branch-based aptitude questions plus technical questions), with results, statistics and pass/fail handling that feed the application status.
- **Camera proctoring:** a separate Python/OpenCV service detects missing/multiple faces and phones using pretrained DNN models (OpenCV's SSD face detector, YOLOv4-tiny for phones — see `camera-proctoring/download_models.py`; falls back to a Haar cascade and a brightness heuristic if the models aren't downloaded). The frontend also reports tab switches and blocked key combinations. Any critical violation terminates the test and marks it `FAILED_PROCTORING` (score 0, application `TEST_FAILED`).
- **External candidate hiring:** bulk-upload resumes for a job, parse and score them, shortlist the top N, generate credentials, assign tests and schedule interviews.
- **Voice assistant** in English, Hindi and Marathi for navigating the site.
- **Roles:** candidate, recruiter, admin.

## Architecture

| Service | Tech | Port |
|---|---|---|
| `frontend/` | React 18, Vite, Tailwind | 3000 |
| `job-recommendation-backend/` | Spring Boot 3.3, Java 17, MySQL 8 | 8081 |
| `camera-proctoring/` | Flask, OpenCV | 5001 |
| MySQL | database `JobEra09` | 3336 (host) |

The frontend talks to the backend (`/api/**`) and directly to the proctoring service. The proctoring service reports terminations back to `POST /api/aptitude-tests/{id}/violations`.

## Running locally (Windows)

Prerequisites: JDK 17, Maven (or the bundled `mvnw`), Node 18+, Python 3.9+, MySQL 8 with an empty database named `JobEra09`.

1. **Backend secrets.** Copy `job-recommendation-backend/secrets.properties.example` to `secrets.properties` and set `DB_PASSWORD`. (The file is git-ignored; environment variables of the same name also work. `DB_URL` and `DB_USERNAME` can be overridden too.)
2. **Start everything:** run `START_SERVICES.bat`. It starts the proctoring service, the backend and the frontend, then open <http://localhost:3000>.

Or start each piece yourself:

```bash
# backend
cd job-recommendation-backend && ./mvnw spring-boot:run

# frontend
cd frontend && npm install && npm run dev

# proctoring
cd camera-proctoring && python -m venv venv && venv\Scripts\activate && pip install -r requirements.txt && python download_models.py && python app.py
```

`download_models.py` fetches the face/phone detection models (~30MB, from OpenCV's and AlexeyAB/darknet's own GitHub-hosted files). It's optional - skip it and the service still runs, just with the older, less accurate Haar-cascade/heuristic detectors.

### Configuration

| Setting | Where | Default |
|---|---|---|
| `VITE_BACKEND_URL` | frontend env | `http://localhost:8081` |
| `VITE_PROCTORING_URL` | frontend env | `http://localhost:5001` |
| `BACKEND_URL` | proctoring env | `http://localhost:8081/api` |
| `PROCTORING_API_KEY` | backend and proctoring env | empty (only loopback callers are trusted) |
| `app.demo-user.email` / `app.demo-user.password` | backend properties | unset (no demo account is created) |

## Docker

```bash
cp .env.example .env      # set MYSQL_ROOT_PASSWORD and PROCTORING_API_KEY
docker compose up --build
```

The proctoring container needs access to a camera device (`/dev/video0`), which is only available on Linux hosts. On Windows and macOS, run the proctoring service directly on the host and point `VITE_PROCTORING_URL` at it.

## Security model

- Every `/api/**` endpoint requires a `Bearer` token issued at login, except `/api/auth/login`, `/api/auth/register` and `/api/health`. Every other endpoint also enforces role and ownership checks (a recruiter can only manage their own jobs/tests/candidates; a candidate can only touch their own applications and assignments) — see `AccessPolicy`.
- The proctoring callback is accepted from loopback, or from any host that sends the shared `X-Proctoring-Key`.
- Passwords are hashed with PBKDF2-HMAC-SHA256 (210,000 iterations, random salt). Hashes from the older SHA-256 scheme still work and are upgraded automatically at the next login.
- Session tokens are stored hashed (SHA-256), expire after 7 days, and are purged when expired.
- Login is rate-limited: 5 failed attempts per account or 30 per client address within 15 minutes returns 429.
- Only profile pictures are publicly served (`/uploads/profiles/**`). Resumes and credential files are only reachable through authenticated, ownership-checked endpoints.

## Tests

```bash
cd job-recommendation-backend && mvn test        # unit tests, no database needed
cd frontend && npm test                          # Vitest + React Testing Library
cd camera-proctoring && pytest test_detection.py # needs download_models.py to have been run
```

## Project layout

```
frontend/                      React app (pages/, components/, services/, config.js)
job-recommendation-backend/    Spring Boot API (controller/, service/, entity/, repository/, config/, security/)
camera-proctoring/             Proctoring service (app.py)
docker-compose.yml             Full stack (MySQL, backend, proctoring, frontend)
START_SERVICES.bat             One-click local start on Windows
```
