# Project rules (always apply)
- Read PROMPT.md for the full spec. Work one numbered step at a time; stop after each step and wait for "continue".
- Test-first: write failing tests, then code. Never claim tests pass without running them (`mvn -q verify`, `npm test`).
- Backend: Java 21, Spring Boot 3, Maven. Core logic (cache, circuit breaker, fallback chain, alerts, magnitude, velocity) is hand-written. No Resilience4j/Caffeine/Guava.
- Never invent upstream behaviour. Use only the payloads in PROMPT.md section 3, or verify with curl.
- Use an injectable java.time.Clock; never Thread.sleep in tests.
- No secrets in the repo. Port 8080. Mock data must always be labelled MOCK.
- After each step: list files created/changed, show test output, and note any assumption to add to README.